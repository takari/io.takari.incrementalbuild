package io.takari.incremental.internal;

import io.takari.incremental.BuildContext;
import io.takari.incremental.BuildContext.Input;

import java.io.File;
import java.io.OutputStream;

public class DefaultOutput implements BuildContext.Output<File> {

  @Override
  public OutputStream newOutputStream() {
    // TODO Auto-generated method stub
    return null;
  }

  public void addCapability(String qualifier, String localName) {
    // TODO Auto-generated method stub
  }

  public Iterable<String> getCapabilities(String qualifier) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Iterable<DefaultInput> getAssociatedInputs() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void associateInput(Input<File> input) {
    if (!(input instanceof DefaultInput)) {
      throw new IllegalArgumentException();
    }

    // TODO Auto-generated method stub

  }
}
