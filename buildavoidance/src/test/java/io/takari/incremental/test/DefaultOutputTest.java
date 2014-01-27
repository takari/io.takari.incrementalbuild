package io.takari.incremental.test;

import io.takari.incremental.BuildContext;
import io.takari.incremental.BuildContext.Input;
import io.takari.incremental.BuildContext.Output;
import io.takari.incremental.internal.DefaultBuildContext;

import java.io.File;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DefaultOutputTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private DefaultBuildContext<?> newBuildContext() {
    File stateFile = new File(temp.getRoot(), "buildstate.ctx");
    return new TestBuildContext(stateFile, Collections.<String, byte[]>emptyMap());
  }

  @Test
  public void testOutputStream_createParentDirectories() throws Exception {
    File inputFile = temp.newFile("inputFile");
    File outputFile = new File(temp.getRoot(), "sub/dir/outputFile");

    BuildContext context = newBuildContext();
    Input<File> input = context.registerInput(inputFile);
    Output<File> output = input.associateOutput(outputFile);
    output.newOutputStream().close();

    Assert.assertTrue(outputFile.canRead());
  }

}
