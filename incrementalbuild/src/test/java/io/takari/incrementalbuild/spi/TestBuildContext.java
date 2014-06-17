package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

class TestBuildContext extends DefaultBuildContext<Exception> {

  public TestBuildContext(File stateFile, Map<String, Serializable> configuration) {
    super(new FilesystemWorkspace(), null /* messageSink */, stateFile, configuration);
  }

  @Override
  protected Exception newBuildFailureException(String message) {
    return new Exception(message);
  }

  @Override
  public boolean isEscalated() {
    return super.isEscalated();
  }
}
