package io.takari.incrementalbuild.cli;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.File;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StandaloneBuildContextTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testTransientBuildContext() throws Exception {
    File file = temp.newFile("file.txt");

    StandaloneBuildContext context = StandaloneBuildContext.transientBuildContext();
    Assert.assertEquals(ResourceStatus.NEW, context.registerInput(file).getStatus());

    context.commit();
  }
}
