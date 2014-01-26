package io.takari.incremental.internal;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

class DefaultBuildContextState implements Serializable, BuildContextStateManager {

  private static final long serialVersionUID = 6195150574931820441L;

  private final Map<String, byte[]> configuration;

  private final Map<File, FileState> files;

  private final Map<File, DefaultOutput> outputs;

  private final Map<File, DefaultInput> inputs;

  private final Map<File, Collection<DefaultOutput>> inputOutputs;

  private final Map<File, Collection<DefaultInput>> outputInputs;

  private final Map<File, Collection<File>> inputIncludedInputs;

  private final Map<QualifiedName, Collection<DefaultInput>> requirementInputs;

  private final Map<File, Collection<QualifiedName>> inputRequirements;

  private final Map<File, Collection<QualifiedName>> outputCapabilities;

  private final Map<File, Map<String, Serializable>> inputAttributes;

  private final Map<File, Collection<Message>> inputMessages;

  public DefaultBuildContextState(Map<String, byte[]> configuration,
      Map<File, DefaultInput> inputs, Map<File, DefaultOutput> outputs,
      Map<File, Collection<DefaultOutput>> inputOutputs,
      Map<File, Collection<DefaultInput>> outputInputs,
      Map<File, Collection<File>> inputIncludedInputs,
      Map<File, Collection<QualifiedName>> inputRequirements,
      Map<QualifiedName, Collection<DefaultInput>> requirementInputs,
      Map<File, Collection<QualifiedName>> outputCapabilities,
      Map<File, Map<String, Serializable>> inputAttributes,
      Map<File, Collection<Message>> inputMessages) {

    this.configuration = unmodifiableMap(configuration); // clone byte[] arrays?
    this.inputs = unmodifiableMap(inputs);
    this.outputs = unmodifiableMap(outputs);
    this.inputOutputs = unmodifiableMultimap(inputOutputs);
    this.outputInputs = unmodifiableMultimap(outputInputs);
    this.inputIncludedInputs = unmodifiableMultimap(inputIncludedInputs);

    this.inputRequirements = unmodifiableMap(inputRequirements);
    this.requirementInputs = unmodifiableMultimap(requirementInputs);
    this.outputCapabilities = unmodifiableMap(outputCapabilities);

    this.inputAttributes = unmodifiableMapMap(inputAttributes);

    this.inputMessages = unmodifiableMap(inputMessages);

    Map<File, FileState> files = new HashMap<File, FileState>();
    putAll(files, inputs.keySet());
    putAll(files, outputs.keySet());
    for (Collection<File> includedInputs : inputIncludedInputs.values()) {
      putAll(files, includedInputs);
    }
    this.files = files;
  }

  private static Map<File, Map<String, Serializable>> unmodifiableMapMap(
      Map<File, Map<String, Serializable>> inputAttributes) {
    Map<File, Map<String, Serializable>> result =
        new LinkedHashMap<File, Map<String, Serializable>>();
    for (Map.Entry<File, Map<String, Serializable>> entry : inputAttributes.entrySet()) {
      result.put(entry.getKey(), unmodifiableMap(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
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

    return !FileState.isPresent(file) || fileState.lastModified != file.lastModified()
        || fileState.length != file.length();
  }

  @Override
  public void addRequirement(DefaultInput defaultInput, String qualifier, String localName) {
    throw new IllegalStateException();
  }

  @Override
  public Iterable<DefaultInput> getDependentInputs(String qualifier, String localName) {
    Collection<DefaultInput> result =
        requirementInputs.get(new QualifiedName(qualifier, localName));
    return result != null ? result : Collections.<DefaultInput>emptyList();
  }

  @Override
  public void addCapability(DefaultOutput output, String qualifier, String localName) {
    throw new IllegalStateException();
  }

  @Override
  public Collection<String> getCapabilities(DefaultOutput output, String qualifier) {
    Collection<QualifiedName> capabilities = outputCapabilities.get(output.getResource());
    if (capabilities == null) {
      return Collections.emptyList();
    }
    Set<String> result = new LinkedHashSet<String>();
    for (QualifiedName capability : capabilities) {
      if (qualifier.equals(capability.getQualifier())) {
        result.add(capability.getLocalName());
      }
    }
    return result;
  }

  // input key/value attrbutes

  @Override
  public <T extends Serializable> T getValue(DefaultInput input, String key, Class<T> clazz) {
    Map<String, Serializable> attributes = inputAttributes.get(input.getResource());
    return attributes != null ? clazz.cast(attributes.get(key)) : null;
  }

  @Override
  public <T extends Serializable> void setValue(DefaultInput input, String key, T value) {
    throw new IllegalStateException();
  }

  // messages

  @Override
  public void addMessage(DefaultInput defaultInput, int line, int column, String message,
      int severity, Throwable cause) {
    throw new IllegalStateException();
  }

  public Collection<DefaultOutput> getAssociatedOutputs(File inputFile) {
    Collection<DefaultOutput> outputs = inputOutputs.get(inputFile);
    return outputs != null ? outputs : Collections.<DefaultOutput>emptyList();
  }

  public Collection<QualifiedName> getOutputCapabilities(File outputFile) {
    return outputCapabilities.get(outputFile);
  }

  public Collection<File> getInputIncludedInputs(File inputFile) {
    return inputIncludedInputs.get(inputFile);
  }

  public Collection<Message> getInputMessages(File inputFile) {
    return inputMessages.get(inputFile);
  }

  public Collection<QualifiedName> getInputRequirements(File inputFile) {
    return inputRequirements.get(inputFile);
  }
}
