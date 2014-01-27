package io.takari.incremental.maven.testing;

import io.takari.incremental.internal.maven.MavenBuildContext;
import io.takari.incremental.internal.maven.MavenIncrementalConventions;
import io.takari.incremental.internal.maven.MojoConfigurationDigester;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;

class TestBuildContext extends MavenBuildContext implements BuildContextLog {

  public TestBuildContext(MojoConfigurationDigester digester,
      MavenIncrementalConventions conventions, MavenProject project, MojoExecution execution)
      throws IOException {
    super(digester, conventions, project, execution);
  }

  @Override
  public Collection<File> getUpdatedOutputs() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Collection<String> getMessages(File file) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void clear() {
    // TODO Auto-generated method stub

  }

}
