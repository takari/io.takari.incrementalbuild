package io.takari.builder.internal.maven;

import org.apache.maven.artifact.Artifact;

import io.takari.builder.IArtifactMetadata;

class MavenArtifactMetadata implements IArtifactMetadata {

  private final Artifact artifact;

  MavenArtifactMetadata(Artifact artifact) {
    this.artifact = artifact;
  }

  @Override
  public String getGroupId() {
    return artifact.getGroupId();
  }

  @Override
  public String getArtifactId() {
    return artifact.getArtifactId();
  }

  @Override
  public String getVersion() {
    return artifact.getVersion();
  }

  @Override
  public String getType() {
    return artifact.getType();
  }

  @Override
  public String getClassifier() {
    return artifact.getClassifier();
  }
}
