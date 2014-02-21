package io.takari.incrementalbuild.spi;

import java.io.Serializable;

public interface Resource<R extends Serializable, H extends Serializable> {

  public R getResourceId();

  public H getDigest();
}
