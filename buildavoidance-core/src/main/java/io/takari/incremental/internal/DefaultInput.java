package io.takari.incremental.internal;

import io.takari.incremental.BuildContext;

import java.io.File;
import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultInput implements BuildContext.Input<File> {

  private transient final DefaultBuildContext context;

  private final ConcurrentMap<File, DefaultOutput> associatedOutputs =
      new ConcurrentHashMap<File, DefaultOutput>();

  public DefaultInput(DefaultBuildContext context) {
    this.context = context;
  }

  @Override
  public void associateIncludedInput(File file) {
    // TODO Auto-generated method stub

  }

  @Override
  public DefaultOutput associateOutput(File file) {
    DefaultOutput output = context.registerOutput(file);
    associateOutput(output);
    return output;
  }

  public void associateOutput(DefaultOutput output) {
    associatedOutputs.put(output.getResource(), output);
  }

  public void addRequirement(String qualifier, String localName) {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean isProcessingRequired() {
    // XXX need different behaviour for new and old instances

    return true;
  }

  @Override
  public File getResource() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public <T extends Serializable> void setValue(String key, T value) {
    // TODO Auto-generated method stub

  }

  @Override
  public <T extends Serializable> T getValue(String key, Class<T> clazz) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void addMessage(int line, int column, String message, int severity, Throwable cause) {
    // TODO Auto-generated method stub

  }

  public boolean isAssociatedOutput(File file) {
    return associatedOutputs.containsKey(file);
  }
}
