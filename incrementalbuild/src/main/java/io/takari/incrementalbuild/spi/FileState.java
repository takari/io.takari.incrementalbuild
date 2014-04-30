package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.File;

class FileState implements ResourceHolder<File> {

  private static final long serialVersionUID = 1;

  final File file;

  final long lastModified;

  final long length;

  public FileState(File file, long lastModified, long length) {
    if (file == null) {
      // throw new IllegalArgumentException("File does not exist or cannot be read " + file);
      throw new NullPointerException();
    }

    this.file = file;
    this.lastModified = lastModified;
    this.length = length;
  }

  @Override
  public File getResource() {
    return file;
  }

  @Override
  public ResourceStatus getStatus() {
    // TODO slightly cleaner approach is to move #getStatus() to a separate optional interface
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    int hash = 31;
    hash = hash * 17 + file.hashCode();
    hash = hash * 17 + (int) lastModified;
    hash = hash * 17 + (int) length;
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FileState)) {
      return false;
    }
    FileState other = (FileState) obj;
    return file.equals(other.file) && lastModified == other.lastModified && length == other.length;
  }
}
