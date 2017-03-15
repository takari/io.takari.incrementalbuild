package io.takari.builder.internal;

import java.io.Serializable;

import io.takari.builder.ResourceType;

@SuppressWarnings("serial")
public class CompileSourceRoot implements Serializable {

  // no serialVersionUID, want deserialization to fail if state format changes

  private final String path;
  private final ResourceType type;

  public CompileSourceRoot(String path, ResourceType type) {
    this.path = path;
    this.type = type;
  }

  public String getPath() {
    return path;
  }

  public ResourceType getType() {
    return type;
  }

}
