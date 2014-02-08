package io.takari.incremental.test;

import static io.takari.incrementalbuild.BuildContext.ResourceStatus.MODIFIED;
import static io.takari.incrementalbuild.BuildContext.ResourceStatus.NEW;
import static io.takari.incrementalbuild.BuildContext.ResourceStatus.UNMODIFIED;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInput;
import io.takari.incrementalbuild.spi.DefaultInputMetadata;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class DefaultBuildContextTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private DefaultBuildContext<?> newBuildContext() {
    File stateFile = new File(temp.getRoot(), "buildstate.ctx");
    return new TestBuildContext(stateFile, Collections.<String, byte[]>emptyMap());
  }

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
    DefaultBuildContext<?> context = newBuildContext();
    Assert.assertNotNull(context.registerInput(file));
    Assert.assertNotNull(context.registerInput(file));
  }

  @Test
  public void testOutputWithoutInputs() throws Exception {
    DefaultBuildContext<?> context = newBuildContext();

    File outputFile = temp.newFile("output_without_inputs");
    context.processOutput(outputFile);

    // is not deleted by repeated deleteStaleOutputs
    context.deleteStaleOutputs(true);
    Assert.assertTrue(outputFile.canRead());
    context.deleteStaleOutputs(true);
    Assert.assertTrue(outputFile.canRead());

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
  public void testDeleteStaleOutputs() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile).process();
    input.associateOutput(outputFile);
    context.commit();

    // deleted input
    Assert.assertTrue(inputFile.delete());
    context = newBuildContext();
    Assert.assertEquals(1, toList(context.deleteStaleOutputs(true)).size());
    Assert.assertFalse(outputFile.canRead());
    // same output can be deleted only once
    Assert.assertEquals(0, toList(context.deleteStaleOutputs(true)).size());

    context.commit();

    // deleted outputs are not carried over
    context = newBuildContext();
    Assert.assertEquals(0, toList(context.deleteStaleOutputs(true)).size());
  }

  private static <T> List<T> toList(Iterable<T> iterable) {
    List<T> result = new ArrayList<T>();
    for (T t : iterable) {
      result.add(t);
    }
    return result;
  }

  @Test
  public void testDeleteStaleOutputs_inputProcessingPending() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile).process();
    input.associateOutput(outputFile);
    context.commit();

    // modify input
    Files.append("test", inputFile, Charsets.UTF_8); // TODO does not matter
    context = newBuildContext();
    input = context.registerInput(inputFile).process();
    context.processInput(input);

    // input was modified and registered for processing
    // but actual processing has not happened yet
    // there is no association between (new) input and (old) output
    // assume the (old) output is orphaned and delete it
    // (new) output will be generate as needed when (new) input is processed
    context.deleteStaleOutputs(true);
    Assert.assertFalse(outputFile.canRead());
  }

  @Test
  public void testDeleteStaleOutputs_retainCarriedOverOutputs() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile).process();
    input.associateOutput(outputFile);
    context.commit();

    context = newBuildContext();
    context.registerInput(inputFile);
    context.deleteStaleOutputs(true);

    Assert.assertTrue(outputFile.canRead());
  }

  @Test
  public void testDeleteStaleOutputs_nonEager() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile).process();
    input.associateOutput(outputFile);
    context.commit();

    context = newBuildContext();
    input = context.registerInput(inputFile).process();

    // stale output is preserved during non-eager delete
    Assert.assertEquals(0, toList(context.deleteStaleOutputs(false)).size());
    Assert.assertTrue(outputFile.canRead());

    // stale output is removed during commit after non-eager delete
    context.commit();
    Assert.assertFalse(outputFile.canRead());
  }

  @Test
  public void testGetInputStatus() throws Exception {
    File inputFile = temp.newFile("inputFile");

    // initial build
    DefaultBuildContext<?> context = newBuildContext();
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
    // Input if input file was modified since last build
    Assert.assertEquals(MODIFIED, context.registerInput(inputFile).getStatus());
  }

  @Test(expected = IllegalStateException.class)
  public void testInputModifiedAfterRegistration() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile).process();
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

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile).process();
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

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile).process();
    input.associateOutput(outputFile1);
    input.associateOutput(outputFile2);
    context.commit();

    context = newBuildContext();
    input = context.registerInput(inputFile).process();
    context.processInput(input);
    input.associateOutput(outputFile1);
    context.commit();

    Assert.assertFalse(outputFile2.canRead());
  }

  @Test
  public void testCreateStateParentDirectory() throws Exception {
    File stateFile = new File(temp.getRoot(), "sub/dir/buildstate.ctx");
    TestBuildContext context =
        new TestBuildContext(stateFile, Collections.<String, byte[]>emptyMap());
    context.commit();
    Assert.assertTrue(stateFile.canRead());
  }

  @Test
  public void testIncludedInputs() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File includedFile = temp.newFile("includedFile");
    Files.append("test", includedFile, Charsets.UTF_8);

    DefaultBuildContext<?> context = newBuildContext();
    context.registerInput(inputFile).process().associateIncludedInput(includedFile);
    context.commit();

    context = newBuildContext();
    Assert.assertEquals(ResourceStatus.UNMODIFIED, context.registerInput(inputFile).getStatus());
    context.commit();

    // check state carry over
    context = newBuildContext();
    Assert.assertEquals(ResourceStatus.UNMODIFIED, context.registerInput(inputFile).getStatus());
    context.commit();

    Files.append("test", includedFile, Charsets.UTF_8);

    context = newBuildContext();
    Assert.assertEquals(ResourceStatus.MODIFIED, context.registerInput(inputFile).getStatus());
  }

  @Test
  public void testGetDependentInputs_deletedInput() throws Exception {
    File inputFile = temp.newFile("inputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile).process();
    input.addRequirement("a", "b");
    context.commit();

    // delete the input
    Assert.assertTrue(inputFile.delete());

    // the input does not exist and therefore does not require the capability
    context = newBuildContext();
    Assert.assertEquals(0, toList(context.getDependentInputs("a", "b")).size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testRegisterInput_nullInput() throws Exception {
    newBuildContext().registerInput(null);
  }

  @Test
  public void testGetRegisteredInputs() throws Exception {
    File inputFile1 = temp.newFile("inputFile1");
    File inputFile2 = temp.newFile("inputFile2");
    File inputFile3 = temp.newFile("inputFile3");
    File inputFile4 = temp.newFile("inputFile4");

    DefaultBuildContext<?> context = newBuildContext();
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

    Map<File, InputMetadata<File>> inputs = new TreeMap<File, InputMetadata<File>>();
    for (InputMetadata<File> input : context.getRegisteredInputs(File.class)) {
      inputs.put(input.getResource(), input);
    }

    Assert.assertEquals(4, inputs.size());
    Assert.assertEquals(ResourceStatus.REMOVED, inputs.get(inputFile1).getStatus());
    Assert.assertEquals(ResourceStatus.UNMODIFIED, inputs.get(inputFile2).getStatus());
    Assert.assertEquals(ResourceStatus.MODIFIED, inputs.get(inputFile3).getStatus());
    Assert.assertEquals(ResourceStatus.NEW, inputs.get(inputFile4).getStatus());
  }

  @Test
  public void testInputAttributes() throws Exception {
    File inputFile = temp.newFile("inputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile).process();
    input.setValue("key", "value");
    context.commit();

    context = newBuildContext();
    DefaultInputMetadata metadata = context.registerInput(inputFile);
    Assert.assertEquals("value", metadata.getValue("key", String.class));
  }
}
