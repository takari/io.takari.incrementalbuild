package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.Severity;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * @noinstantiate clients are not expected to instantiate this class
 */
public class DefaultOutput extends DefaultOutputMetadata implements BuildContext.Output<File> {

  DefaultOutput(DefaultBuildContext<?> context, DefaultBuildContextState state, File file) {
    super(context, state, file);
  }

  @Override
  public OutputStream newOutputStream() throws IOException {
    return context.newOutputStream(this);
  }

  public void addCapability(String qualifier, String localName) {
    context.addCapability(this, qualifier, localName);
  }

  @Override
  public <I> void associateInput(InputMetadata<I> input) {
    if (!(input instanceof DefaultInput<?>)) {
      throw new IllegalArgumentException();
    }
    context.associate((DefaultInput<?>) input, this);
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
  public <V extends Serializable> V getAttribute(String key, Class<V> clazz) {
    return context.getResourceAttribute(file, key, false /* this build */, clazz);
  }

  @Override
  public <V extends Serializable> Serializable setAttribute(String key, V value) {
    return context.setResourceAttribute(file, key, value);
  }

  @Override
  public void addMessage(int line, int column, String message, Severity severity, Throwable cause) {
    context.addMessage(getResource(), line, column, message, severity, cause);
  }
}
