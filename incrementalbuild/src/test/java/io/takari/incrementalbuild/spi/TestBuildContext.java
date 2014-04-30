package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

class TestBuildContext extends DefaultBuildContext<Exception> {

  public TestBuildContext(File stateFile, Map<String, Serializable> configuration) {
    super(new FilesystemWorkspace(), stateFile, configuration);
  }

  @Override
  protected Exception newBuildFailureException(int errorCount) {
    return new Exception();
  }

  @Override
  public boolean isEscalated() {
    return super.isEscalated();
  }
}
