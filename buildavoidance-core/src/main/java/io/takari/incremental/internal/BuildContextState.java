package io.takari.incremental.internal;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

class BuildContextState implements Serializable {

  private final Map<String, byte[]> configuration;

  private final Map<File, DefaultOutput> outputs;

  private final Map<File, DefaultInput> inputs;

  public BuildContextState(Map<String, byte[]> configuration, Map<File, DefaultOutput> outputs) {
    // TODO does serialization/desirilization preserve unmodifiable collections?

    this.configuration = Collections.unmodifiableMap(configuration);
    this.outputs = Collections.unmodifiableMap(outputs);
  }

  public Map<String, byte[]> getConfiguration() {
    return configuration;
  }

  public Map<File, DefaultOutput> getOutputs() {
    return outputs;
  }

  public Map<File, DefaultInput> getInputs() {
    return inputs;
  }
}
