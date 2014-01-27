package io.takari.incremental.test;

import io.takari.incremental.spi.DefaultBuildContext;
import io.takari.incremental.spi.DefaultInput;

import java.io.File;
import java.util.Map;

class TestBuildContext extends DefaultBuildContext<Exception> {

  public TestBuildContext(File stateFile, Map<String, byte[]> configuration) {
    super(stateFile, configuration);
  }

  @Override
  protected void logMessage(DefaultInput input, int line, int column, String message, int severity,
      Throwable cause) {}

  @Override
  protected Exception newBuildFailureException(int errorCount) {
    return new Exception();
  }
}
