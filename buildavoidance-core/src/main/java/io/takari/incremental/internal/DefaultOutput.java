package io.takari.incremental.internal;

import io.takari.incremental.BuildContext;
import io.takari.incremental.BuildContext.Input;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @noinstantiate clients are not supposed to instantiate instances of this class
 */
public class DefaultOutput implements BuildContext.Output<File> {

  private transient final BuildContextStateManager state;

  private final File file;

  DefaultOutput(DefaultBuildContext context, File file) {
    this.state = context;
    this.file = file;
  }

  @Override
  public File getResource() {
    return file;
  }

  @Override
  public OutputStream newOutputStream() throws IOException {
    // XXX oldState must be read-only
    return new FileOutputStream(file);
  }

  public void addCapability(String qualifier, String localName) {
    state.addCapability(this, qualifier, localName);
  }

  public Iterable<String> getCapabilities(String qualifier) {
    return state.getCapabilities(this, qualifier);
  }

  @Override
  public Iterable<DefaultInput> getAssociatedInputs() {
    return state.getAssociatedInputs(this);
  }

  @Override
  public void associateInput(Input<File> input) {
    if (!(input instanceof DefaultInput)) {
      throw new IllegalArgumentException();
    }

    state.associate((DefaultInput) input, this);
  }

  @Override
  public int hashCode() {
    return file.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof DefaultOutput)) {
      return false;
    }

    DefaultOutput other = (DefaultOutput) obj;

    // must be from the same context to be equal
    return state == other.state && file.equals(other.file);
  }
}
