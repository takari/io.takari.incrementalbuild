package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.File;
import java.io.Serializable;

/**
 * @noinstantiate clients are not expected to instantiate this class
 */
public class DefaultInput implements BuildContext.Input<File> {

  final DefaultBuildContext<?> context;

  private final File file;

  public DefaultInput(DefaultBuildContext<?> context, File file) {
    this.context = context;
    this.file = file;
  }

  @Override
  public void associateIncludedInput(File file) {
    context.associateIncludedInput(this, file);
  }

  @Override
  public DefaultOutput associateOutput(File file) {
    return context.associateOutput(this, file);
  }

  public void associateOutput(DefaultOutput output) {
    context.associate(this, output);
  }

  @Override
  public Iterable<DefaultOutput> getAssociatedOutputs() {
    return context.getAssociatedOutputs(file);
  }

  public void addRequirement(String qualifier, String localName) {
    context.addRequirement(this, qualifier, localName);
  }

  @Override
  public <T extends Serializable> Serializable setValue(String key, T value) {
    return context.setResourceAttribute(file, key, value);
  }

  @Override
  public <T extends Serializable> T getValue(String key, Class<T> clazz) {
    return context.getResourceAttribute(file, key, clazz);
  }

  @Override
  public void addMessage(int line, int column, String message, int severity, Throwable cause) {
    context.addMessage(this, line, column, message, severity, cause);
  }

  public boolean isAssociatedOutput(File file) {
    return context.isAssociatedOutput(this, file);
  }

  @Override
  public ResourceStatus getStatus() {
    return context.getInputStatus(getResource());
  }

  @Override
  public File getResource() {
    return file;
  }


  @Override
  public int hashCode() {
    return getResource().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof DefaultInput)) {
      return false;
    }

    DefaultInput other = (DefaultInput) obj;

    // must be from the same context to be equal
    return context == other.context && file.equals(other.file);
  }

  @Override
  public Input<File> process() {
    return this;
  }

}
