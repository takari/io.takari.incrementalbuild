package io.takari.incremental.maven.testing;

import io.takari.incremental.internal.DefaultOutput;
import io.takari.incremental.internal.maven.MavenBuildContext;
import io.takari.incremental.internal.maven.MavenIncrementalConventions;
import io.takari.incremental.internal.maven.MojoConfigurationDigester;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;

class TestBuildContext extends MavenBuildContext {

  private final BuildContextLog logger;

  @Inject
  public TestBuildContext(MojoConfigurationDigester digester,
      MavenIncrementalConventions conventions, MavenProject project, MojoExecution execution,
      BuildContextLog logger) throws IOException {
    super(digester, conventions, project, execution);
    this.logger = logger;
  }

  @Override
  public DefaultOutput registerOutput(File outputFile) {
    DefaultOutput output = super.registerOutput(outputFile);
    logger.addRegisterOutput(output.getResource());
    return output;
  }

  @Override
  protected void deleteStaleOutput(File outputFile) throws IOException {
    logger.addDeletedOutput(outputFile);
    super.deleteStaleOutput(outputFile);
  }
}
