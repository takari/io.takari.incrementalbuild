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

  private transient final DefaultBuildContext context;

  private final File file;

  DefaultOutput(DefaultBuildContext context, File file) {
    this.context = context;
    this.file = file;
  }

  @Override
  public File getResource() {
    return file;
  }

  @Override
  public OutputStream newOutputStream() throws IOException {
    return new FileOutputStream(file);
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
    return context.getAssociatedInputs(this);
  }

  @Override
  public void associateInput(Input<File> input) {
    if (!(input instanceof DefaultInput)) {
      throw new IllegalArgumentException();
    }

    context.associate((DefaultInput) input, this);
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
    return context == other.context && file.equals(other.file);
  }
}
