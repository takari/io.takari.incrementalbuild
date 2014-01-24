package io.takari.incremental.internal;

import io.takari.incremental.BuildContext;
import io.takari.incremental.FileSet;
import io.takari.incremental.FileSetBuilder;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DefaultBuildContext implements BuildContext {

  /**
   * Maps requirement qname to all input that require it.
   */
  private final Map<QualifiedName, Set<DefaultInput>> requirementInputs = new HashMap<>();

  @Override
  public DefaultInput processInput(File file) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Iterable<DefaultInput> processInputs(FileSet fileSet) {
    // TODO Auto-generated method stub
    return null;
  }

  // low-level methods

  public Iterable<DefaultOutput> deleteStaleOutputs() {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Returns {@code Input}s with this requirement
   */
  public Iterable<DefaultInput> getDependencies(String qualifier, Serializable localName) {
    Set<DefaultInput> result = requirementInputs.get(new QualifiedName(qualifier, localName));
    return result != null ? result : Collections.<DefaultInput>emptyList();
  }

  @Override
  public DefaultOutput registerOutput(File file) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DefaultOutput getOldOutput(File file) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DefaultInput registerInput(File file) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public FileSetBuilder fileSetBuilder() {
    // TODO Auto-generated method stub
    return null;
  }
}
