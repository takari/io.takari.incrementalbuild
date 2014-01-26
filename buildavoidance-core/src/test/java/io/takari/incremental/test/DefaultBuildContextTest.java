package io.takari.incremental.test;

import io.takari.incremental.internal.DefaultBuildContext;
import io.takari.incremental.internal.DefaultInput;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DefaultBuildContextTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private static class TestBuildContext extends DefaultBuildContext<Exception> {

    public TestBuildContext(File stateFile, Map<String, byte[]> configuration) {
      super(stateFile, configuration);
    }

    @Override
    protected void logMessage(DefaultInput input, int line, int column, String message,
        int severity, Throwable cause) {}

    @Override
    protected Exception newBuildFailureException() {
      return new Exception();
    }
  }

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

    Assert.assertTrue(inputFile.delete());

    context = newBuildContext();
    context.deleteOrphanedOutputs();

    Assert.assertFalse(outputFile.canRead());
  }

  @Test
  public void testDeleteOrphanedOutputs_retainStaleOutputs() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = temp.newFile("outputFile");

    DefaultBuildContext<?> context = newBuildContext();
    DefaultInput input = context.registerInput(inputFile);
    input.associateOutput(outputFile);
    context.commit();

    context = newBuildContext();
    context.registerInput(inputFile);
    context.deleteOrphanedOutputs();

    Assert.assertTrue(outputFile.canRead());
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
    context.deleteOrphanedOutputs();

    Assert.assertTrue(outputFile.canRead());
  }

}
