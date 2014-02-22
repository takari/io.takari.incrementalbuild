package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class DefaultBuildContextState implements Serializable {

  private static final long serialVersionUID = 6195150574931820441L;

  final Map<String, byte[]> configuration;

  final Map<File, FileState> outputs = new HashMap<File, FileState>();

  final Map<Object, FileState> inputs = new HashMap<Object, FileState>();

  final Map<Object, Collection<File>> inputOutputs = new HashMap<Object, Collection<File>>();

  final Map<File, Collection<Object>> outputInputs = new HashMap<File, Collection<Object>>();

  final Map<Object, Collection<Object>> inputIncludedInputs =
      new HashMap<Object, Collection<Object>>();

  final Map<QualifiedName, Collection<Object>> requirementInputs =
      new HashMap<QualifiedName, Collection<Object>>();

  final Map<Object, Collection<QualifiedName>> inputRequirements =
      new HashMap<Object, Collection<QualifiedName>>();

  final Map<File, Collection<QualifiedName>> outputCapabilities =
      new HashMap<File, Collection<QualifiedName>>();

  final Map<Object, Map<String, Serializable>> resourceAttributes =
      new HashMap<Object, Map<String, Serializable>>();

  final Map<Object, Collection<Message>> inputMessages = new HashMap<Object, Collection<Message>>();

  public DefaultBuildContextState(Map<String, byte[]> configuration) {
    this.configuration = new HashMap<String, byte[]>(configuration);
  }

}
