package io.takari.incremental.internal;

import io.takari.incremental.BuildContext;
import io.takari.incremental.BuildContext.Input;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @noinstantiate clients are not supposed to instantiate instances of this class
 */
public class DefaultOutput implements BuildContext.Output<File> {

  private final File file;

  private final ConcurrentMap<File, DefaultInput> associatedInputs =
      new ConcurrentHashMap<File, DefaultInput>();

  DefaultOutput(File file) {
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
    return Collections.unmodifiableCollection(associatedInputs.values());
  }

  @Override
  public void associateInput(Input<File> input) {
    if (!(input instanceof DefaultInput)) {
      throw new IllegalArgumentException();
    }

    final DefaultInput defaultInput = (DefaultInput) input;
    associatedInputs.put(input.getResource(), defaultInput);

    if (defaultInput.isAssociatedOutput(file)) {
      defaultInput.associateOutput(file);
    }
  }
}
