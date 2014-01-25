package io.takari.incremental.internal;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

class BuildContextState implements Serializable, BuildContextStateManager {

  private final Map<String, byte[]> configuration;

  private final Map<File, FileState> files;

  private final Map<File, DefaultOutput> outputs;

  private final Map<File, DefaultInput> inputs;

  private final Map<File, Collection<DefaultOutput>> inputOutputs;

  private final Map<File, Collection<DefaultInput>> outputInputs;

  private final Map<File, Collection<File>> inputIncludedInputs;

  public BuildContextState(Map<String, byte[]> configuration, Map<File, DefaultInput> inputs,
      Map<File, DefaultOutput> outputs, Map<File, Collection<DefaultOutput>> inputOutputs,
      Map<File, Collection<DefaultInput>> outputInputs,
      Map<File, Collection<File>> inputIncludedInputs) {

    this.configuration = unmodifiableMap(configuration); // clone byte[] arrays?
    this.inputs = unmodifiableMap(inputs);
    this.outputs = unmodifiableMap(outputs);
    this.inputOutputs = unmodifiableMultimap(inputOutputs);
    this.outputInputs = unmodifiableMultimap(outputInputs);
    this.inputIncludedInputs = unmodifiableMultimap(inputIncludedInputs);

    Map<File, FileState> files = new HashMap<File, FileState>();
    putAll(files, inputs.keySet());
    putAll(files, outputs.keySet());
    for (Collection<File> includedInputs : inputIncludedInputs.values()) {
      putAll(files, includedInputs);
    }

    this.files = files;
  }

  private static <K, V> Map<K, V> unmodifiableMap(Map<K, V> map) {
    return Collections.unmodifiableMap(new LinkedHashMap<K, V>(map));
  }

  private static <K, V> Map<K, Collection<V>> unmodifiableMultimap(Map<K, Collection<V>> map) {
    HashMap<K, Collection<V>> result = new LinkedHashMap<K, Collection<V>>();
    for (Map.Entry<K, Collection<V>> entry : map.entrySet()) {
      Collection<V> values = new ArrayList<V>(entry.getValue());
      result.put(entry.getKey(), Collections.unmodifiableCollection(values));
    }
    return Collections.unmodifiableMap(result);
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
    DefaultOutput output = outputs.get(outputFile);
    Collection<DefaultOutput> outputs = inputOutputs.get(input.getResource());
    // is it legal to have null output or outputs here?
    return output != null && outputs != null && outputs.contains(output);
  }

  @Override
  public Collection<DefaultInput> getAssociatedInputs(DefaultOutput output) {
    Collection<DefaultInput> inputs = outputInputs.get(output.getResource());
    return inputs != null ? inputs : Collections.<DefaultInput>emptyList();
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
