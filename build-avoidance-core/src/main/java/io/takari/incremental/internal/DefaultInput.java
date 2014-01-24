package io.takari.incremental.internal;

import io.takari.incremental.BuildContext;

import java.io.File;

public class DefaultInput implements BuildContext.Input {

  @Override
  public void addIncludedInput(File file) {
    // TODO Auto-generated method stub

  }

  @Override
  public DefaultOutput registerOutput(File file) {
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

}
