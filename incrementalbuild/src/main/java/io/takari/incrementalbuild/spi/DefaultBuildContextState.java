package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class DefaultBuildContextState implements Serializable {

  private static final long serialVersionUID = 6195150574931820441L;

  final Map<String, byte[]> configuration;

  final Map<File, ResourceHolder<File>> outputs = new HashMap<File, ResourceHolder<File>>();

  final Map<Object, ResourceHolder<?>> inputs = new HashMap<Object, ResourceHolder<?>>();

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

  private DefaultBuildContextState(Map<String, byte[]> configuration) {
    this.configuration = configuration;
  }

  public static DefaultBuildContextState withConfiguration(Map<String, byte[]> configuration) {
    HashMap<String, byte[]> copy = new HashMap<String, byte[]>(configuration);
    // configuration marker used to distinguish between empty and new state
    copy.put("incremental", new byte[] {1});
    return new DefaultBuildContextState(Collections.unmodifiableMap(copy));
  }

  public static DefaultBuildContextState emptyState() {
    // TODO make state immutable
    return new DefaultBuildContextState(Collections.<String, byte[]>emptyMap());
  }

}
