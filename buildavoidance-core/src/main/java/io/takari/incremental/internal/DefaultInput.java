package io.takari.incremental.internal;

import io.takari.incremental.BuildContext;

import java.io.File;
import java.io.Serializable;

public class DefaultInput implements BuildContext.Input<File> {

  @Override
  public void associateIncludedInput(File file) {
    // TODO Auto-generated method stub

  }

  @Override
  public DefaultOutput associateOutput(File file) {
    // TODO Auto-generated method stub
    return null;
  }

  public void addRequirement(String qualifier, String localName) {
    // TODO Auto-generated method stub

  }

  @Override
  public boolean isProcessingRequired() {
    // TODO Auto-generated method stub
    return false;
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
}
