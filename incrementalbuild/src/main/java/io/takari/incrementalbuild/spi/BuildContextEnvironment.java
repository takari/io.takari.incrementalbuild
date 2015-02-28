package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.workspace.Workspace;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

public interface BuildContextEnvironment {

  public File getStateFile();

  // I don't like this here, but otherwise all implementations will have to explicitly depend on
  // Workspace, which I believe is worse.
  public Workspace getWorkspace();

  public Map<String, Serializable> getParameters();

  /**
   * Optional context finalizer.
   */
  public BuildContextFinalizer getFinalizer();
}
