package io.takari.incremental.internal;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

class BuildContextState implements Serializable {

  private final Map<String, byte[]> configuration;

  public BuildContextState(Map<String, byte[]> configuration) {
    // TODO clone byte arrays?
    this.configuration = new HashMap<String, byte[]>(configuration);
  }

  public Map<String, byte[]> getConfiguration() {
    return configuration;
  }
}
