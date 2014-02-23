package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.Serializable;

public interface ResourceHolder<R> extends Serializable {

  public R getResource();

  public ResourceStatus getStatus();
}
