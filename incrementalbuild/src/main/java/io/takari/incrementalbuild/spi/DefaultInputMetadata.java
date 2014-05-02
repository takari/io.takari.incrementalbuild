package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.OutputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;

/**
 * @noinstantiate clients are not expected to instantiate this class
 */
public class DefaultInputMetadata<T> implements InputMetadata<T> {

  final DefaultBuildContext<?> context;

  final DefaultBuildContextState state;

  final T resource;

  DefaultInputMetadata(DefaultBuildContext<?> context, DefaultBuildContextState state, T resource) {
    this.context = context;
    this.state = state;
    this.resource = resource;
  }

  @Override
  public T getResource() {
    return resource;
  }

  @Override
  public Iterable<? extends OutputMetadata<File>> getAssociatedOutputs() {
    return context.getAssociatedOutputs(state, resource);
  }

  @Override
  public ResourceStatus getStatus() {
    return context.getInputStatus(resource, true /* associated */);
  }

  @Override
  public <V extends Serializable> V getAttribute(String key, Class<V> clazz) {
    return context.getResourceAttribute(resource, key, true /* previous */, clazz);
  }

  @Override
  public DefaultInput<T> process() {
    return context.processInput(this);
  }

  public Collection<String> getRequiredCapabilities(String qualifier) {
    return context.getRequirements(this, state, qualifier);
  }
}
