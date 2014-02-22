package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

class DefaultBuildContextState implements Serializable {

  private static final long serialVersionUID = 6195150574931820441L;

  private final Map<String, byte[]> configuration;

  final Map<File, FileState> outputs = new HashMap<File, FileState>();

  final Map<File, FileState> inputs = new HashMap<File, FileState>();

  final Map<File, Collection<File>> inputOutputs = new HashMap<File, Collection<File>>();

  final Map<File, Collection<File>> outputInputs = new HashMap<File, Collection<File>>();

  final Map<File, Collection<File>> inputIncludedInputs = new HashMap<File, Collection<File>>();

  final Map<QualifiedName, Collection<File>> requirementInputs =
      new HashMap<QualifiedName, Collection<File>>();

  final Map<File, Collection<QualifiedName>> inputRequirements =
      new HashMap<File, Collection<QualifiedName>>();

  final Map<File, Collection<QualifiedName>> outputCapabilities =
      new HashMap<File, Collection<QualifiedName>>();

  final Map<File, Map<String, Serializable>> resourceAttributes =
      new HashMap<File, Map<String, Serializable>>();

  final Map<File, Collection<Message>> inputMessages = new HashMap<File, Collection<Message>>();

  public DefaultBuildContextState(Map<String, byte[]> configuration) {
    this.configuration = new HashMap<String, byte[]>(configuration);
  }

  public Map<String, byte[]> getConfiguration() {
    return configuration;
  }

  public Set<File> getOutputFiles() {
    return outputs.keySet();
  }

  public Set<File> getInputFiles() {
    return inputs.keySet();
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
    return getResourceStatus(outputs, outputFile);
  }

  private BuildContext.ResourceStatus getResourceStatus(Map<File, FileState> files, File file) {
    FileState oldState = files.get(file);
    if (oldState == null) {
      return BuildContext.ResourceStatus.NEW;
    }
    if (!FileState.isPresent(file)) {
      return BuildContext.ResourceStatus.REMOVED;
    }
    return oldState.isUptodate(file)
        ? BuildContext.ResourceStatus.UNMODIFIED
        : BuildContext.ResourceStatus.MODIFIED;
  }

  public BuildContext.ResourceStatus getInputStatus(File inputFile) {
    BuildContext.ResourceStatus status = getResourceStatus(inputs, inputFile);
    if (status == BuildContext.ResourceStatus.UNMODIFIED) {
      Collection<File> includedInputs = inputIncludedInputs.get(inputFile);
      if (includedInputs != null) {
        for (File includedInput : includedInputs) {
          if (getResourceStatus(inputs, includedInput) != BuildContext.ResourceStatus.UNMODIFIED) {
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

  public <T extends Serializable> T getResourceAttribute(File resource, String key, Class<T> clazz) {
    Map<String, Serializable> attributes = resourceAttributes.get(resource);
    return attributes != null ? clazz.cast(attributes.get(key)) : null;
  }

  public Map<String, Serializable> getResourceAttributes(File resource) {
    return resourceAttributes.get(resource);
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

  public FileState getInputState(File file) {
    return inputs.get(file);
  }


}
