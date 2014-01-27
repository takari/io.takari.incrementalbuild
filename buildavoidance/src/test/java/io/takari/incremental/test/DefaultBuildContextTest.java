package io.takari.incremental.test;

import io.takari.incremental.internal.DefaultBuildContext;
import io.takari.incremental.internal.DefaultInput;
import io.takari.incremental.internal.DefaultOutput;

import java.io.File;
import java.util.Collections;

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
    Assert.assertSame(context.registerInput(file), context.registerInput(file));
  }

  @Test
  public void testOutputWithoutInputs() throws Exception {
    DefaultBuildContext<?> context = newBuildContext();

    File outputFile = temp.newFile("output_without_inputs");
    context.registerOutput(outputFile);

    // is not deleted by repeated deleteStaleOutputs
    context.deleteOrphanedOutputs();
    Assert.assertTrue(outputFile.canRead());
    context.deleteOrphanedOutputs();
    Assert.assertTrue(outputFile.canRead());

    // is not deleted by commit
    context.commit();
    Assert.assertTrue(outputFile.canRead());

    // is not deleted after rebuild with re-registration
    context = newBuildContext();
    context.registerOutput(outputFile);
    context.commit();
    Assert.assertTrue(outputFile.canRead());

    // deleted after rebuild without re-registration
    context = newBuildContext();
    context.commit();
    Assert.assertFalse(outputFile.canRead());
  }

  @Test
  public void testDeleteOrphanedOutputs() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile);
    input.associateOutput(outputFile);
    context.commit();

    // deleted input
    Assert.assertTrue(inputFile.delete());
    context = newBuildContext();
    context.deleteOrphanedOutputs();

    Assert.assertFalse(outputFile.canRead());
  }

  @Test
  public void testDeleteOrphanedOutputs_inputProcessingPending() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile);
    input.associateOutput(outputFile);
    context.commit();

    // modify input
    Files.append("test", inputFile, Charsets.UTF_8);
    context = newBuildContext();
    Assert.assertNotNull(context.processInput(inputFile));

    // input was modified and registered for processing
    // but actual processing has not happened yet
    // there is no association between (new) input and (old) output
    // assume the (old) output is orphaned and delete it
    // (new) output will be generate as needed when (new) input is processed
    context.deleteOrphanedOutputs();
    Assert.assertFalse(outputFile.canRead());
  }

  @Test
  public void testDeleteOrphanedOutputs_retainCarriedOverOutputs() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile);
    input.associateOutput(outputFile);
    context.commit();

    context = newBuildContext();
    Assert.assertNull(context.processInput(inputFile));
    context.deleteOrphanedOutputs();

    Assert.assertTrue(outputFile.canRead());
  }

  @Test
  public void testProcessInput() throws Exception {
    File inputFile = temp.newFile("inputFile");

    // initial build
    DefaultBuildContext<?> context = newBuildContext();
    // first time invocation returns Input for processing
    Assert.assertNotNull(context.processInput(inputFile));
    // second invocation returns null
    Assert.assertNull(context.processInput(inputFile));
    context.commit();

    // new build
    context = newBuildContext();
    // null if input file was not modified since last build
    Assert.assertNull(context.processInput(inputFile));
    context.commit();

    // new build
    Files.append("test", inputFile, Charsets.UTF_8);
    context = newBuildContext();
    // Input if input file was modified since last build
    Assert.assertNotNull(context.processInput(inputFile));
    Assert.assertNull(context.processInput(inputFile));
  }

  @Test(expected = IllegalStateException.class)
  public void testInputModifiedAfterRegistration() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile);
    input.associateOutput(outputFile);
    context.commit();

    context = newBuildContext();
    Assert.assertNull(context.processInput(inputFile));

    // this is incorrect use of build-avoidance API
    // even though the input has changed, it was changed after it was registered for processing
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
    DefaultInput input = context.registerInput(inputFile);
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
    DefaultInput input = context.registerInput(inputFile);
    input.associateOutput(outputFile1);
    input.associateOutput(outputFile2);
    context.commit();

    context = newBuildContext();
    input = context.registerInput(inputFile);
    input.associateOutput(outputFile1);
    context.commit();

    Assert.assertFalse(outputFile2.canRead());
  }

  @Test
  public void testInputProcessingRequired_deletedInput() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile);
    input.associateOutput(outputFile);
    context.commit();

    Assert.assertTrue(inputFile.delete());

    context = newBuildContext();
    DefaultOutput oldOutput = context.getOldOutput(outputFile);
    DefaultInput oldInput = oldOutput.getAssociatedInputs().iterator().next();
    Assert.assertEquals(input.getResource(), oldInput.getResource());
    Assert.assertFalse(oldInput.isProcessingRequired());
  }

  @Test
  public void testCreateStateParentDirectory() throws Exception {
    File stateFile = new File(temp.getRoot(), "sub/dir/buildstate.ctx");
    TestBuildContext context =
        new TestBuildContext(stateFile, Collections.<String, byte[]>emptyMap());
    context.commit();
    Assert.assertTrue(stateFile.canRead());
  }
}
