package io.takari.incremental.internal;

import java.io.File;
import java.io.Serializable;

class FileState implements Serializable {

  private final long lastModified;
  private final long length;

  public FileState(File file) {
    this.lastModified = file.lastModified();
    this.length = file.length();
  }
}
