package io.takari.incremental.internal;

import java.io.Serializable;
import java.util.Map;

class BuildContextState implements Serializable {

  private final Map<String, byte[]> configuration;

  public BuildContextState(Map<String, byte[]> configuration) {
    this.configuration = configuration;
  }

  public Map<String, byte[]> getConfiguration() {
    return configuration;
  }
}
