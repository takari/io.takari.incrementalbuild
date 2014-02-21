package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collection;

/**
 * @noinstantiate clients are not expected to instantiate this class
 */
public class DefaultOutput implements BuildContext.Output<File>, Resource, CapabilitiesProvider {

  private final DefaultBuildContext<?> context;
  private final File file;

  DefaultOutput(DefaultBuildContext<?> context, File file) {
    this.context = context;
    this.file = file;
  }

  @Override
  public OutputStream newOutputStream() throws IOException {
    File parent = getResource().getParentFile();
    if (!parent.isDirectory() && !parent.mkdirs()) {
      throw new IOException("Could not create directory " + parent);
    }
    return new FileOutputStream(getResource());
  }

  public void addCapability(String qualifier, String localName) {
    context.addCapability(this, qualifier, localName);
  }

  @Override
  public Collection<String> getCapabilities(String namespace) {
    return context.getOutputCapabilities(file, namespace);
  }

  @Override
  public Iterable<DefaultInput> getAssociatedInputs() {
    return context.getAssociatedInputs(getResource());
  }

  @Override
  public void associateInput(InputMetadata<File> input) {
    context.associate(input, this);
  }

  @Override
  public File getResource() {
    return file;
  }

  @Override
  public ResourceStatus getStatus() {
    // TODO Auto-generated method stub
    return null;
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

  @Override
  public <V extends Serializable> V getValue(String key, Class<V> clazz) {
    return context.getResourceAttribute(file, key, clazz);
  }

  @Override
  public <V extends Serializable> Serializable setValue(String key, V value) {
    return context.setResourceAttribute(file, key, value);
  }
}
