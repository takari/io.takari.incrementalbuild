package io.takari.builder.internal.digest;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

@SuppressWarnings("serial")
public class FileDigest implements Serializable {

  // no serialVersionUID, want deserialization to fail if state format changes

  public final long length;

  public final long lastModified;

  @Override
  public int hashCode() {
    int hash = 31;
    hash = hash * 17 + (int) lastModified;
    hash = hash * 17 + (int) length;
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof FileDigest)) {
      return false;
    }
    FileDigest other = (FileDigest) obj;
    return lastModified == other.lastModified && length == other.length;
  }

  private FileDigest(long length, long lastModified) {
    this.length = length;
    this.lastModified = lastModified;
  }

  public static FileDigest digest(Path file) {
    try {
      return new FileDigest(Files.size(file), Files.getLastModifiedTime(file).toMillis());
    } catch (IOException e) {
      return new FileDigest(0, 0);
    }
  }

}
