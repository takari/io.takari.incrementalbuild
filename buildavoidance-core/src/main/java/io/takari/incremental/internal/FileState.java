package io.takari.incremental.internal;

import java.io.File;
import java.io.Serializable;

class FileState implements Serializable {

  final long lastModified;
  final long length;

  public FileState(File file) {
    this.lastModified = file.lastModified();
    this.length = file.length();
  }
}
