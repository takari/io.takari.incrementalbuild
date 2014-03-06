package io.takari.incrementalbuild.maven.testing;

import io.takari.incrementalbuild.maven.internal.MavenBuildContext;
import io.takari.incrementalbuild.maven.internal.MavenIncrementalConventions;
import io.takari.incrementalbuild.maven.internal.MojoConfigurationDigester;
import io.takari.incrementalbuild.spi.DefaultOutput;

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
  public DefaultOutput processOutput(File outputFile) {
    DefaultOutput output = super.processOutput(outputFile);
    logger.addRegisterOutput(output.getResource());
    return output;
  }

  @Override
  protected void deleteStaleOutput(File outputFile) throws IOException {
    logger.addDeletedOutput(outputFile);
    super.deleteStaleOutput(outputFile);
  }

  @Override
  protected void carryOverOutput(Object inputResource, File outputFile) {
    logger.addCarryoverOutput(outputFile);
    super.carryOverOutput(inputResource, outputFile);
  }

  @Override
  protected void logMessage(Object inputResource, int line, int column, String message,
      Severity severity, Throwable cause) {
    if (!(inputResource instanceof File)) {
      // XXX I am too lazy right now, need to fix this later
      throw new IllegalArgumentException();
    }
    File file = (File) inputResource;
    String msg = String.format("%s %s [%d:%d] %s", severity.name(), //
        file.getName(), line, column, message);
    logger.addMessage(file, msg);
    super.logMessage(inputResource, line, column, message, severity, cause);
  }
}
