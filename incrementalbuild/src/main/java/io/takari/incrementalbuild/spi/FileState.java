package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.Serializable;

class FileState implements Serializable {

  private static final long serialVersionUID = -3901167354884462923L;

  final long lastModified;

  final long length;

  public FileState(File file) {
    this.lastModified = file.lastModified();
    this.length = file.length();
  }

  public static boolean equals(FileState a, FileState b) {
    return a.length == b.length && a.lastModified == b.lastModified;
  }

  public boolean isUptodate(File file) {
    return isPresent(file) && length == file.length() && lastModified == file.lastModified();
  }

  // TODO find a better place
  public static boolean isPresent(File file) {
    return file.isFile() && file.canRead();
  }


}
