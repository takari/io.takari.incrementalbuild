package io.takari.builder.internal.resolver;

import javax.inject.Named;

@Named
public interface ArtifactResolverProvider<T> {
  public DependencyResolver getResolver(T project);
}
