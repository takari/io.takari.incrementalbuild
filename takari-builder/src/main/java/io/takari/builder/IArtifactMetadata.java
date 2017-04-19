package io.takari.builder;

public interface IArtifactMetadata {
  public String getGroupId();
  public String getArtifactId();
  public String getVersion();
  public String getType();
  public String getClassifier();
}
