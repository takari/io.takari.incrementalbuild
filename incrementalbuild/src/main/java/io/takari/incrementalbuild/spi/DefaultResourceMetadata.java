package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.ResourceStatus;

/**
 * @noinstantiate clients are not expected to instantiate this class
 */
public class DefaultResourceMetadata<T> implements ResourceMetadata<T> {

  protected final AbstractBuildContext context;

  protected final DefaultBuildContextState state;

  protected final T resource;

  protected DefaultResourceMetadata(AbstractBuildContext context, DefaultBuildContextState state,
      T resource) {
    this.context = context;
    this.state = state;
    this.resource = resource;
  }

  @Override
  public T getResource() {
    return resource;
  }

  @Override
  public ResourceStatus getStatus() {
    return context.getResourceStatus(resource);
  }

  @Override
  public DefaultResource<T> process() {
    return context.processResource(this);
  }

  @Override
  public int hashCode() {
    return resource.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }

    DefaultResourceMetadata<?> other = (DefaultResourceMetadata<?>) obj;

    // must be from the same context to be equal
    return context == other.context && state == other.state && resource.equals(other.resource);
  }

  protected AbstractBuildContext getContext() {
    return context;
  }

  @Override
  public String toString() {
    return resource.toString();
  }
}
