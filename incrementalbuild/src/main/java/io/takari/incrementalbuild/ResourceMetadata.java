package io.takari.incrementalbuild;

public interface ResourceMetadata<T> {

  public T getResource();

  public ResourceStatus getStatus();

  public Resource<T> process();

}
