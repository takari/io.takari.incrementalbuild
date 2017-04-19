package io.takari.builder.internal;

import java.net.URL;
import java.util.Collections;
import java.util.Set;

import io.takari.builder.IArtifactResources;
import io.takari.builder.IArtifactMetadata;

class ArtifactResourcesImpl implements IArtifactResources {

  public final IArtifactMetadata artifact;

  public final Set<URL> urls;

  public ArtifactResourcesImpl(IArtifactMetadata artifact, Set<URL> urls) {
    this.artifact = artifact;
    this.urls = Collections.unmodifiableSet(urls);
  }

  @Override
  public IArtifactMetadata artifact() {
    return artifact;
  }

  @Override
  public Set<URL> resources() {
    return urls;
  }

}
