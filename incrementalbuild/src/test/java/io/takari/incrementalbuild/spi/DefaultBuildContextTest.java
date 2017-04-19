package io.takari.incrementalbuild.spi;

import static io.takari.incrementalbuild.ResourceStatus.MODIFIED;
import static io.takari.incrementalbuild.ResourceStatus.NEW;
import static io.takari.incrementalbuild.ResourceStatus.UNMODIFIED;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.ResourceStatus;

public class DefaultBuildContextTest extends AbstractBuildContextTest {

  @Test(expected = IllegalArgumentException.class)
  public void testRegisterInput_inputFileDoesNotExist() throws Exception {
    File file = new File("target/does_not_exist");
    Assert.assertTrue(!file.exists() && !file.canRead());
    newBuildContext().registerInput(file);
  }

  @Test
  public void testRegisterInput() throws Exception {
    // this is NOT part of API but rather currently implemented behaviour
    // API allows #registerInput return different instances

    File file = new File("src/test/resources/simplelogger.properties");
    Assert.assertTrue(file.exists() && file.canRead());
    TestBuildContext context = newBuildContext();
    Assert.assertNotNull(context.registerInput(file));
    Assert.assertNotNull(context.registerInput(file));
  }

  @Test
  public void testOutputWithoutInputs() throws Exception {
    TestBuildContext context = newBuildContext();

    File outputFile = temp.newFile("output_without_inputs");
    context.processOutput(outputFile);

    // is not deleted by commit
    context.commit();
    Assert.assertTrue(outputFile.canRead());

    // is not deleted after rebuild with re-registration
    context = newBuildContext();
    context.processOutput(outputFile);
    context.commit();
    Assert.assertTrue(outputFile.canRead());

    // deleted after rebuild without re-registration
    context = newBuildContext();
    context.commit();
    Assert.assertFalse(outputFile.canRead());
  }

  @Test
  public void testGetInputStatus() throws Exception {
    File inputFile = temp.newFile("inputFile");

    // initial build
    TestBuildContext context = newBuildContext();
    // first time invocation returns Input for processing
    Assert.assertEquals(NEW, context.registerInput(inputFile).getStatus());
    // second invocation still returns NEW
    Assert.assertEquals(NEW, context.registerInput(inputFile).getStatus());
    context.commit();

    // new build
    context = newBuildContext();
    // input file was not modified since last build
    Assert.assertEquals(UNMODIFIED, context.registerInput(inputFile).getStatus());
    context.commit();

    // new build
    Files.append("test", inputFile, Charsets.UTF_8);
    context = newBuildContext();
    // input file was modified since last build
    Assert.assertEquals(MODIFIED, context.registerInput(inputFile).getStatus());
  }

  @Test(expected = IllegalStateException.class)
  public void testInputModifiedAfterRegistration() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    TestBuildContext context = newBuildContext();
    DefaultResource<File> input = context.registerInput(inputFile).process();
    input.associateOutput(outputFile);
    context.commit();

    context = newBuildContext();
    context.registerInput(inputFile);

    // this is incorrect use of build-avoidance API
    // input has changed after it was registered for processing
    // IllegalStateException is raised to prevent unexpected process/not-process flip-flop
    Files.append("test", inputFile, Charsets.UTF_8);
    context.commit();
    Assert.assertTrue(outputFile.canRead());
  }

  @Test
  public void testCommit_orphanedOutputsCleanup() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    TestBuildContext context = newBuildContext();
    DefaultResource<File> input = context.registerInput(inputFile).process();
    input.associateOutput(outputFile);
    context.commit();

    // input is not part of input set any more
    // associated output must be cleaned up
    context = newBuildContext();
    context.commit();
    Assert.assertFalse(outputFile.canRead());
  }

  @Test
  public void testCommit_staleOutputCleanup() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile1 = temp.newFile("outputFile1");
    File outputFile2 = temp.newFile("outputFile2");

    TestBuildContext context = newBuildContext();
    DefaultResource<File> input = context.registerInput(inputFile).process();
    input.associateOutput(outputFile1);
    input.associateOutput(outputFile2);
    context.commit();

    context = newBuildContext();
    input = context.registerInput(inputFile).process();
    context.processResource(input);
    input.associateOutput(outputFile1);
    context.commit();
    Assert.assertFalse(outputFile2.canRead());

    context = newBuildContext();
    DefaultResourceMetadata<File> metadata = context.registerInput(inputFile);
    Assert.assertEquals(1, toList(context.getAssociatedOutputs(metadata)).size());
    context.commit();
  }

  @Test
  public void testCreateStateParentDirectory() throws Exception {
    File stateFile = new File(temp.getRoot(), "sub/dir/buildstate.ctx");
    TestBuildContext context =
        new TestBuildContext(stateFile, Collections.<String, Serializable>emptyMap());
    context.commit();
    Assert.assertTrue(stateFile.canRead());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRegisterInput_nullInput() throws Exception {
    newBuildContext().registerInput((File) null);
  }

  @Test
  public void testRegisterAndProcessInputs() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");
    List<String> includes = Arrays.asList(inputFile.getName());

    TestBuildContext context = newBuildContext();
    List<DefaultResource<File>> inputs =
        toList(context.registerAndProcessInputs(temp.getRoot(), includes, null));
    Assert.assertEquals(1, inputs.size());
    Assert.assertEquals(ResourceStatus.NEW, inputs.get(0).getStatus());
    inputs.get(0).associateOutput(outputFile);
    context.commit();

    // no change rebuild
    context = newBuildContext();
    inputs = toList(context.registerAndProcessInputs(temp.getRoot(), includes, null));
    Assert.assertEquals(0, inputs.size());
    context.commit();
  }

  @Test
  public void testGetAssociatedOutputs() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    TestBuildContext context = newBuildContext();
    context.registerInput(inputFile).process().associateOutput(outputFile);
    context.commit();

    //
    context = newBuildContext();
    DefaultResourceMetadata<File> metadata = context.registerInput(inputFile);
    List<? extends ResourceMetadata<File>> outputs = toList(context.getAssociatedOutputs(metadata));
    Assert.assertEquals(1, outputs.size());
    Assert.assertEquals(ResourceStatus.UNMODIFIED, outputs.get(0).getStatus());
    context.commit();

    //
    Files.append("test", outputFile, Charsets.UTF_8);
    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    outputs = toList(context.getAssociatedOutputs(metadata));
    Assert.assertEquals(1, outputs.size());
    Assert.assertEquals(ResourceStatus.MODIFIED, outputs.get(0).getStatus());
    context.commit();

    //
    Assert.assertTrue(outputFile.delete());
    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    outputs = toList(context.getAssociatedOutputs(metadata));
    Assert.assertEquals(1, outputs.size());
    Assert.assertEquals(ResourceStatus.REMOVED, outputs.get(0).getStatus());
    context.commit();
  }

  @Test
  public void testGetRegisteredInputs() throws Exception {
    File inputFile1 = temp.newFile("inputFile1");
    File inputFile2 = temp.newFile("inputFile2");
    File inputFile3 = temp.newFile("inputFile3");
    File inputFile4 = temp.newFile("inputFile4");

    TestBuildContext context = newBuildContext();
    inputFile1 = context.registerInput(inputFile1).getResource();
    inputFile2 = context.registerInput(inputFile2).getResource();
    inputFile3 = context.registerInput(inputFile3).getResource();
    context.commit();

    Files.append("test", inputFile3, Charsets.UTF_8);

    context = newBuildContext();

    // context.registerInput(inputFile1); DELETED
    context.registerInput(inputFile2); // UNMODIFIED
    context.registerInput(inputFile3); // MODIFIED
    inputFile4 = context.registerInput(inputFile4).getResource(); // NEW

    Map<File, ResourceMetadata<File>> inputs = new TreeMap<File, ResourceMetadata<File>>();
    for (ResourceMetadata<File> input : context.getRegisteredInputs()) {
      inputs.put(input.getResource(), input);
    }

    Assert.assertEquals(4, inputs.size());
    // Assert.assertEquals(ResourceStatus.REMOVED, inputs.get(inputFile1).getStatus());
    Assert.assertEquals(ResourceStatus.UNMODIFIED, inputs.get(inputFile2).getStatus());
    Assert.assertEquals(ResourceStatus.MODIFIED, inputs.get(inputFile3).getStatus());
    Assert.assertEquals(ResourceStatus.NEW, inputs.get(inputFile4).getStatus());
  }

  @Test
  public void testInputAttributes() throws Exception {
    File inputFile = temp.newFile("inputFile");

    TestBuildContext context = newBuildContext();
    DefaultResourceMetadata<File> metadata = context.registerInput(inputFile);
    Assert.assertNull(context.getAttribute(metadata, "key", String.class));
    DefaultResource<File> input = metadata.process();
    Assert.assertNull(context.setAttribute(input, "key", "value"));
    context.commit();

    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    Assert.assertEquals("value", context.getAttribute(metadata, "key", String.class));
    context.commit();

    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    Assert.assertEquals("value", context.getAttribute(metadata, "key", String.class));
    input = metadata.process();
    Assert.assertNull(context.getAttribute(input, "key", String.class));
    Assert.assertEquals("value", context.setAttribute(input, "key", "newValue"));
    Assert.assertEquals("value", context.setAttribute(input, "key", "newValue"));
    Assert.assertEquals("newValue", context.getAttribute(input, "key", String.class));
    context.commit();

    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    Assert.assertEquals("newValue", context.getAttribute(metadata, "key", String.class));
    context.commit();
  }


  @Test
  public void testOutputStatus() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = new File(temp.getRoot(), "outputFile");

    Assert.assertFalse(outputFile.canRead());

    TestBuildContext context = newBuildContext();
    DefaultOutput output = context.registerInput(inputFile).process().associateOutput(outputFile);
    Assert.assertEquals(ResourceStatus.NEW, output.getStatus());
    output.newOutputStream().close();
    context.commit();

    // no-change rebuild
    context = newBuildContext();
    output = context.registerInput(inputFile).process().associateOutput(outputFile);
    Assert.assertEquals(ResourceStatus.UNMODIFIED, output.getStatus());
    context.commit();

    // modified output
    Files.write("test", outputFile, Charsets.UTF_8);
    context = newBuildContext();
    output = context.registerInput(inputFile).process().associateOutput(outputFile);
    Assert.assertEquals(ResourceStatus.MODIFIED, output.getStatus());
    context.commit();

    // no-change rebuild
    context = newBuildContext();
    output = context.registerInput(inputFile).process().associateOutput(outputFile);
    Assert.assertEquals(ResourceStatus.UNMODIFIED, output.getStatus());
    context.commit();

    // deleted output
    Assert.assertTrue(outputFile.delete());
    context = newBuildContext();
    output = context.registerInput(inputFile).process().associateOutput(outputFile);
    Assert.assertEquals(ResourceStatus.REMOVED, output.getStatus());
    output.newOutputStream().close(); // processed outputs must exit or commit fails
    context.commit();
  }

  @Test
  public void testStateSerialization_useTCCL() throws Exception {
    File inputFile = temp.newFile("inputFile");

    TestBuildContext context = newBuildContext();

    URL dummyJar = new File("src/test/projects/dummy/dummy-1.0.jar").toURI().toURL();
    ClassLoader tccl = new URLClassLoader(new URL[] {dummyJar});
    ClassLoader origTCCL = Thread.currentThread().getContextClassLoader();
    try {
      Thread.currentThread().setContextClassLoader(tccl);

      Object dummy = tccl.loadClass("dummy.Dummy").newInstance();

      DefaultResource<File> input = context.registerInput(inputFile).process();
      context.setAttribute(input, "dummy", (Serializable) dummy);
      context.commit();

      context = newBuildContext();
      Assert.assertFalse(((TestBuildContext) context).isEscalated());
      Assert.assertNotNull(
          context.getAttribute(context.registerInput(inputFile), "dummy", Serializable.class));
      // no commit
    } finally {
      Thread.currentThread().setContextClassLoader(origTCCL);
    }

    // sanity check, make sure empty state is loaded without proper TCCL
    context = newBuildContext();
    Assert.assertTrue(((TestBuildContext) context).isEscalated());
  }

  @Test
  public void testConfigurationChange() throws Exception {
    File inputFile = temp.newFile("input");
    File outputFile = temp.newFile("output");
    File looseOutputFile = temp.newFile("looseOutputFile");

    TestBuildContext context = newBuildContext();
    context.registerInput(inputFile).process().associateOutput(outputFile);
    context.processOutput(looseOutputFile);
    context.commit();

    context =
        newBuildContext(Collections.<String, Serializable>singletonMap("config", "parameter"));
    DefaultResourceMetadata<File> metadata = context.registerInput(inputFile);
    Assert.assertEquals(ResourceStatus.MODIFIED, metadata.getStatus());
    DefaultResource<File> input = metadata.process();
    Assert.assertEquals(ResourceStatus.MODIFIED, input.getStatus());
    DefaultOutput output = input.associateOutput(outputFile);
    Assert.assertEquals(ResourceStatus.MODIFIED, output.getStatus());
    DefaultOutput looseOutput = context.processOutput(looseOutputFile);
    Assert.assertEquals(ResourceStatus.MODIFIED, looseOutput.getStatus());
  }


  @Test
  public void testRegisterInputs_includes_excludes() throws Exception {
    temp.newFolder("folder");
    File f1 = temp.newFile("input1.txt");
    File f2 = temp.newFile("folder/input2.txt");
    File f3 = temp.newFile("folder/input3.log");

    TestBuildContext context = newBuildContext();
    List<File> actual;

    actual = toFileList(context.registerInputs(temp.getRoot(), null, Arrays.asList("**")));
    assertIncludedPaths(Collections.<File>emptyList(), actual);

    actual = toFileList(context.registerInputs(temp.getRoot(), null, null));
    assertIncludedPaths(Arrays.asList(f1, f2, f3), actual);

    actual = toFileList(context.registerInputs(temp.getRoot(), Arrays.asList("**/*.txt"), null));
    assertIncludedPaths(Arrays.asList(f1, f2), actual);

    actual = toFileList(
        context.registerInputs(temp.getRoot(), Arrays.asList("**"), Arrays.asList("**/*.log")));
    assertIncludedPaths(Arrays.asList(f1, f2), actual);
  }

  @Test
  public void testRegisterInputs_directoryMatching() throws Exception {
    temp.newFolder("folder");
    temp.newFolder("folder/subfolder");
    File f1 = temp.newFile("input1.txt");
    File f2 = temp.newFile("folder/input2.txt");
    File f3 = temp.newFile("folder/subfolder/input3.txt");

    TestBuildContext context = newBuildContext();
    List<File> actual;

    // from http://ant.apache.org/manual/dirtasks.html#patterns
    // When ** is used as the name of a directory in the pattern, it matches zero or more
    // directories.

    actual = toFileList(context.registerInputs(temp.getRoot(), Arrays.asList("**/*.txt"), null));
    assertIncludedPaths(Arrays.asList(f1, f2, f3), actual);

    actual =
        toFileList(context.registerInputs(temp.getRoot(), Arrays.asList("folder/**/*.txt"), null));
    assertIncludedPaths(Arrays.asList(f2, f3), actual);

    actual =
        toFileList(context.registerInputs(temp.getRoot(), Arrays.asList("folder/*.txt"), null));
    assertIncludedPaths(Arrays.asList(f2), actual);

    // / is a shortcut for /**
    actual = toFileList(context.registerInputs(temp.getRoot(), Arrays.asList("/"), null));
    assertIncludedPaths(Arrays.asList(f1, f2, f3), actual);
    actual = toFileList(context.registerInputs(temp.getRoot(), Arrays.asList("folder/"), null));
    assertIncludedPaths(Arrays.asList(f2, f3), actual);

    // leading / does not matter
    actual = toFileList(context.registerInputs(temp.getRoot(), Arrays.asList("/folder/"), null));
    assertIncludedPaths(Arrays.asList(f2, f3), actual);
  }

  private List<File> toFileList(Iterable<DefaultResourceMetadata<File>> inputs) {
    List<File> files = new ArrayList<>();
    for (DefaultResourceMetadata<File> input : inputs) {
      files.add(input.getResource());
    }
    return files;
  }

  private static void assertIncludedPaths(Collection<File> expected, Collection<File> actual)
      throws IOException {
    Assert.assertEquals(toString(new TreeSet<File>(expected)), toString(new TreeSet<File>(actual)));
  }

  private static String toString(Iterable<? extends File> files) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (File file : files) {
      sb.append(file.getCanonicalPath()).append('\n');
    }
    return sb.toString();
  }

  @Test
  public void testClosedContext() throws Exception {
    TestBuildContext context = newBuildContext();

    context.commit();
    try {
      context.registerInput(temp.newFile());
      Assert.fail();
    } catch (IllegalStateException e) {
      // expected
    }
    try {
      context.processOutput(temp.newFile());
      Assert.fail();
    } catch (IllegalStateException e) {
      // expected
    }
  }

  @Test
  public void testSkipExecution() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    TestBuildContext context = newBuildContext();
    DefaultResource<File> input = context.registerInput(inputFile).process();
    input.associateOutput(outputFile);
    context.commit();

    // make a change
    Files.append("test", inputFile, Charsets.UTF_8);

    // skip execution
    context = newBuildContext();
    context.markSkipExecution();
    context.commit();
    Assert.assertTrue(outputFile.canRead());

    //
    context = newBuildContext();
    DefaultResourceMetadata<File> inputMetadata = context.registerInput(inputFile);
    Assert.assertEquals(ResourceStatus.MODIFIED, inputMetadata.getStatus());
    inputMetadata.process();
    context.commit();
    Assert.assertFalse(outputFile.canRead());
  }

  @Test
  public void testSkipExecution_modifiedContext() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    TestBuildContext context = newBuildContext();
    DefaultResource<File> input = context.registerInput(inputFile).process();
    input.associateOutput(outputFile);

    try {
      context.markSkipExecution();
      Assert.fail();
    } catch (IllegalStateException e) {
      // expected
    }
  }

  @Test
  public void testInputDirectoryDoesNotExist() throws Exception {
    File basedir = new File(temp.getRoot(), "does-not-exist");
    Assert.assertFalse(basedir.exists()); // sanity check

    TestBuildContext context = newBuildContext();
    Assert.assertEquals(0, toList(context.registerInputs(basedir, null, null)).size());
    Assert.assertEquals(0, toList(context.registerAndProcessInputs(basedir, null, null)).size());
  }

  @Test
  public void testDeletedOutput() throws Exception {
    TestBuildContext context = newBuildContext();
    File outputFile = temp.newFile("output_without_inputs");
    context.processOutput(outputFile);
    context.commit();

    Assert.assertTrue(outputFile.delete());
    context = newBuildContext();
    Assert.assertTrue(context.isEscalated());
  }
}
