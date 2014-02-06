package io.takari.incremental.test;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.Output;
import io.takari.incrementalbuild.spi.DefaultBuildContext;

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
    Input<File> input = context.registerInput(inputFile).process();
    Output<File> output = input.associateOutput(outputFile);
    output.newOutputStream().close();

    Assert.assertTrue(outputFile.canRead());
  }

}