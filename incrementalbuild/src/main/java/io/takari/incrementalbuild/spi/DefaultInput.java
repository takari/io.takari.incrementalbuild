package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Output;
import io.takari.incrementalbuild.BuildContext.Severity;

import java.io.File;
import java.io.Serializable;

/**
 * @noinstantiate clients are not expected to instantiate this class
 */
public class DefaultInput<T> extends DefaultInputMetadata<T> implements BuildContext.Input<T> {

  DefaultInput(DefaultBuildContext<?> context, DefaultBuildContextState state, T resource) {
    super(context, state, resource);
  }

  @Override
  public void associateIncludedInput(File includedFile) {
    DefaultInput<File> included = context.processIncludedInput(includedFile);
    context.associateIncludedInput(this, included);
  }

  @Override
  public DefaultOutput associateOutput(Output<File> output) {
    if (!(output instanceof DefaultOutput)) {
      throw new IllegalArgumentException();
    }
    context.associate(this, (DefaultOutput) output);
    return (DefaultOutput) output;
  }

  @Override
  public DefaultOutput associateOutput(File outputFile) {
    DefaultOutput output = context.processOutput(outputFile);
    context.associate(this, output);
    return output;
  }

  public void addRequirement(String qualifier, String localName) {
    context.addRequirement(this, qualifier, localName);
  }

  @Override
  public <V extends Serializable> Serializable setValue(String key, V value) {
    return context.setResourceAttribute(resource, key, value);
  }

  @Override
  public void addMessage(int line, int column, String message, Severity severity, Throwable cause) {
    context.addMessage(getResource(), line, column, message, severity, cause);
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

    DefaultInput<?> other = (DefaultInput<?>) obj;

    // must be from the same context to be equal
    return context == other.context && resource.equals(other.resource);
  }

  @Override
  public <V extends Serializable> V getValue(String key, Class<V> clazz) {
    return context.getResourceAttribute(resource, key, false /* this build */, clazz);
  }
}
