package io.takari.incremental.internal;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class BuildContextState implements Serializable, BuildContextStateManager {

  private final Map<String, byte[]> configuration;

  private final Map<File, FileState> files;

  private final Map<File, DefaultOutput> outputs;

  private final Map<File, DefaultInput> inputs;

  private final Map<File, Collection<DefaultOutput>> inputOutputs;

  private final Map<File, Collection<File>> inputIncludedInputs;

  public BuildContextState(Map<String, byte[]> configuration, Map<File, DefaultInput> inputs,
      Map<File, DefaultOutput> outputs, Map<File, Collection<DefaultOutput>> inputOutputs,
      Map<File, Collection<File>> inputIncludedInputs) {

    // TODO does serialization/desirilization preserve unmodifiable collections?
    // TODO create deep unmodifiable clones

    this.configuration = configuration;
    this.inputs = inputs;
    this.outputs = outputs;
    this.inputOutputs = inputOutputs;
    this.inputIncludedInputs = inputIncludedInputs;

    Map<File, FileState> files = new HashMap<File, FileState>();
    putAll(files, inputs.keySet());
    putAll(files, outputs.keySet());
    for (Collection<File> includedInputs : inputIncludedInputs.values()) {
      putAll(files, includedInputs);
    }

    this.files = files;
  }

  private static void putAll(Map<File, FileState> state, Collection<File> files) {
    for (File file : files) {
      state.put(file, new FileState(file));
    }
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

  @Override
  public DefaultOutput associateOutput(DefaultInput input, File outputFile) {
    throw new IllegalStateException();
  }

  @Override
  public void associate(DefaultInput input, DefaultOutput output) {
    throw new IllegalStateException();
  }

  @Override
  public boolean isAssociatedOutput(DefaultInput input, File outputFile) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public Collection<DefaultInput> getAssociatedInputs(DefaultOutput output) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void associateIncludedInput(DefaultInput input, File includedFile) {
    throw new IllegalStateException();
  }

  @Override
  public boolean isProcessingRequired(DefaultInput input) {
    final File inputFile = input.getResource();

    // processing required if input itself changed or removed, any associated outputs changed or
    // removed, any included inputs changed or removed

    // input itself
    if (isProcessingRequired(inputFile)) {
      return true;
    }

    // associated outputs
    Collection<DefaultOutput> outputs = inputOutputs.get(inputFile);
    if (outputs != null) {
      for (DefaultOutput output : outputs) {
        if (isProcessingRequired(output.getResource())) {
          return true;
        }
      }
    }

    // included inputs
    Collection<File> IncludedInputs = inputIncludedInputs.get(inputFile);
    if (IncludedInputs != null) {
      for (File includedInput : IncludedInputs) {
        if (isProcessingRequired(includedInput)) {
          return true;
        }
      }
    }

    return false;
  }

  private boolean isProcessingRequired(File file) {
    FileState fileState = files.get(file);

    if (fileState == null) {
      // the requested file was not part of the old state
      throw new IllegalArgumentException();
    }

    return !file.canRead() || fileState.lastModified != file.lastModified()
        || fileState.length != file.length();
  }

}
