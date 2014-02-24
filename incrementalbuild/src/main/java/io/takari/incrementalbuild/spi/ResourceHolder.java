package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.Serializable;

/**
 * Wraps input or output resource and logic and state necessary to determine if the resource has or
 * has not changed since the holder instance was created.
 *
 * @param <R> must provide correct implementation of {@code hashCode()} and {@code equals(Object)}
 */
public interface ResourceHolder<R extends Serializable> extends Serializable {

  public R getResource();

  // TODO introduce K getResourceKey(), relax R serializable requirement

  public ResourceStatus getStatus();
}
