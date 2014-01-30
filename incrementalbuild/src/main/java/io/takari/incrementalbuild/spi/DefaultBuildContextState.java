package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

class DefaultBuildContextState implements Serializable {

  private static final long serialVersionUID = 6195150574931820441L;

  private final Map<String, byte[]> configuration;

  private final Map<File, FileState> files;

  private final Set<File> outputs;

  private final Set<File> inputs;

  private final Map<File, Collection<File>> inputOutputs;

  private final Map<File, Collection<File>> outputInputs;

  private final Map<File, Collection<File>> inputIncludedInputs;

  private final Map<QualifiedName, Collection<File>> requirementInputs;

  private final Map<File, Collection<QualifiedName>> inputRequirements;

  private final Map<File, Collection<QualifiedName>> outputCapabilities;

  private final Map<File, Map<String, Serializable>> inputAttributes;

  private final Map<File, Collection<Message>> inputMessages;

  public DefaultBuildContextState(Map<String, byte[]> configuration, Map<File, FileState> files,
      Set<File> inputs, Set<File> outputs, Map<File, Collection<File>> inputOutputs,
      Map<File, Collection<File>> outputInputs, Map<File, Collection<File>> inputIncludedInputs,
      Map<File, Collection<QualifiedName>> inputRequirements,
      Map<QualifiedName, Collection<File>> requirementInputs,
      Map<File, Collection<QualifiedName>> outputCapabilities,
      Map<File, Map<String, Serializable>> inputAttributes,
      Map<File, Collection<Message>> inputMessages) {

    this.configuration = cloneMap(configuration); // clone byte[] arrays?
    this.files = cloneMap(files);
    this.inputs = cloneSet(inputs);
    this.outputs = cloneSet(outputs);
    this.inputOutputs = cloneInputOutputs(inputOutputs);
    this.outputInputs = cloneOutputInputs(outputInputs);
    this.inputIncludedInputs = cloneInputIncludedInputs(inputIncludedInputs);

    this.inputRequirements = cloneMap(inputRequirements);
    this.requirementInputs = cloneRequirementInputs(requirementInputs);
    this.outputCapabilities = cloneMap(outputCapabilities);

    this.inputAttributes = cloneAttributes(inputAttributes);

    this.inputMessages = cloneMap(inputMessages);
  }

  // MOST HORRIBLE MESS START

  private static Map<QualifiedName, Collection<File>> cloneRequirementInputs(
      Map<QualifiedName, Collection<File>> requirementInputs) {
    Map<QualifiedName, Collection<File>> result =
        new LinkedHashMap<QualifiedName, Collection<File>>();
    for (Map.Entry<QualifiedName, Collection<File>> entry : requirementInputs.entrySet()) {
      result.put(entry.getKey(), cloneFiles(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<File, Collection<File>> cloneInputIncludedInputs(
      Map<File, Collection<File>> inputIncludedInputs) {
    Map<File, Collection<File>> result = new LinkedHashMap<File, Collection<File>>();
    for (Map.Entry<File, Collection<File>> entry : inputIncludedInputs.entrySet()) {
      result.put(entry.getKey(), Collections.unmodifiableCollection(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<File, Collection<File>> cloneOutputInputs(
      Map<File, Collection<File>> outputInputs) {
    Map<File, Collection<File>> result = new LinkedHashMap<File, Collection<File>>();
    for (Map.Entry<File, Collection<File>> entry : outputInputs.entrySet()) {
      result.put(entry.getKey(), cloneFiles(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  private static Map<File, Collection<File>> cloneInputOutputs(
      Map<File, Collection<File>> inputOutputs) {
    Map<File, Collection<File>> result = new LinkedHashMap<File, Collection<File>>();
    for (Map.Entry<File, Collection<File>> entry : inputOutputs.entrySet()) {
      result.put(entry.getKey(), cloneFiles(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  private static Collection<File> cloneFiles(Collection<File> files) {
    return Collections.unmodifiableSet(new LinkedHashSet<File>(files));
  }

  private static <T> Set<T> cloneSet(Set<T> set) {
    return Collections.unmodifiableSet(new LinkedHashSet<T>(set));
  }

  private static Map<File, Map<String, Serializable>> cloneAttributes(
      Map<File, Map<String, Serializable>> inputAttributes) {
    Map<File, Map<String, Serializable>> result =
        new LinkedHashMap<File, Map<String, Serializable>>();
    for (Map.Entry<File, Map<String, Serializable>> entry : inputAttributes.entrySet()) {
      result.put(entry.getKey(), cloneMap(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  private static <K, V> Map<K, V> cloneMap(Map<K, V> map) {
    return Collections.unmodifiableMap(new LinkedHashMap<K, V>(map));
  }

  // MOST HORRIBLE MESS END


  public Map<String, byte[]> getConfiguration() {
    return configuration;
  }

  public Set<File> getOutputFiles() {
    return outputs;
  }

  public Set<File> getInputFiles() {
    return inputs;
  }

  public boolean isAssociatedOutput(DefaultInput input, File outputFile) {
    Collection<File> outputs = inputOutputs.get(input.getResource());
    return outputs != null && outputs.contains(outputFile);
  }

  public Collection<File> getAssociatedInputs(File outputFile) {
    Collection<File> inputs = outputInputs.get(outputFile);
    if (inputs == null || inputs.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableCollection(inputs);
  }

  public BuildContext.ResourceStatus getOutputStatus(File outputFile) {
    return getResourceStatus(outputFile);
  }

  private BuildContext.ResourceStatus getResourceStatus(File outputFile) {
    FileState oldState = files.get(outputFile);
    if (oldState == null) {
      return BuildContext.ResourceStatus.NEW;
    }
    if (!FileState.isPresent(outputFile)) {
      return BuildContext.ResourceStatus.REMOVED;
    }
    return oldState.isUptodate(outputFile)
        ? BuildContext.ResourceStatus.UNMODIFIED
        : BuildContext.ResourceStatus.MODIFIED;
  }

  public BuildContext.ResourceStatus getInputStatus(File inputFile) {
    BuildContext.ResourceStatus status = getResourceStatus(inputFile);
    if (status == BuildContext.ResourceStatus.UNMODIFIED) {
      Collection<File> includedInputs = inputIncludedInputs.get(inputFile);
      if (includedInputs != null) {
        for (File includedInput : includedInputs) {
          if (getResourceStatus(includedInput) != BuildContext.ResourceStatus.UNMODIFIED) {
            status = BuildContext.ResourceStatus.MODIFIED;
            break;
          }
        }
      }
    }
    return status;
  }

  public Collection<String> getCapabilities(File outputFile, String qualifier) {
    Collection<QualifiedName> capabilities = outputCapabilities.get(outputFile);
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

  public Collection<File> getDependentInputs(String qualifier, String localName) {
    Collection<File> dependents = requirementInputs.get(new QualifiedName(qualifier, localName));
    if (dependents == null || dependents.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableCollection(dependents);
  }

  // input key/value attrbutes

  public <T extends Serializable> T getValue(File inputFile, String key, Class<T> clazz) {
    Map<String, Serializable> attributes = inputAttributes.get(inputFile);
    return attributes != null ? clazz.cast(attributes.get(key)) : null;
  }

  // messages

  public Collection<File> getAssociatedOutputs(File inputFile) {
    Collection<File> outputFiles = inputOutputs.get(inputFile);
    if (outputFiles == null || outputFiles.isEmpty()) {
      return Collections.<File>emptyList();
    }
    return outputFiles;
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

  public FileState getFileState(File file) {
    return files.get(file);
  }


}
