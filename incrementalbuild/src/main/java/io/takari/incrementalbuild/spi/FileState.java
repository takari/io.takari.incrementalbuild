package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.File;

class FileState implements ResourceHolder<File> {

  private static final long serialVersionUID = 1;

  final File file;

  final long lastModified;

  final long length;

  public FileState(File file) {
    if (file == null || !isPresent(file)) {
      throw new IllegalArgumentException("File does not exist or cannot be read " + file);
    }

    this.file = file;
    this.lastModified = file.lastModified();
    this.length = file.length();
  }

  private boolean isUptodate(File file) {
    return isPresent(file) && length == file.length() && lastModified == file.lastModified();
  }

  private static boolean isPresent(File file) {
    return file != null && file.isFile() && file.canRead();
  }

  @Override
  public File getResource() {
    return file;
  }

  @Override
  public ResourceStatus getStatus() {
    if (!isPresent(file)) {
      return ResourceStatus.REMOVED;
    }
    return isUptodate(file) ? ResourceStatus.UNMODIFIED : ResourceStatus.MODIFIED;
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
