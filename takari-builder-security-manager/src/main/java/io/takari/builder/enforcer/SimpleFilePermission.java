package io.takari.builder.enforcer;

import java.io.File;
import java.io.FilePermission;
import java.security.AccessController;
import java.security.Permission;

/**
 * Simple file read/write Permission implementation. Wraps provided path as-is. Does not implement
 * {@link #implies(Permission)}.
 * 
 * Performance optimization meant to avoid instantiation of {@link FilePermission}, which involves
 * expansive {@link AccessController#doPrivileged(java.security.PrivilegedAction)} and
 * {@link File#getCanonicalPath()} calls.
 */
abstract class SimpleFilePermission extends Permission {

  private static final long serialVersionUID = 1L;
  protected String action;

  static final class FileReadPermission extends SimpleFilePermission {

    private static final long serialVersionUID = 1L;
    protected final String action = "read";

    public FileReadPermission(String path) {
      super(path);
    }
  }

  static final class FileWritePermission extends SimpleFilePermission {

    private static final long serialVersionUID = 1L;
    protected final String action = "write";

    public FileWritePermission(String path) {
      super(path);
    }
  }

  private SimpleFilePermission(String path) {
    super(path);
  }

  @Override
  public boolean implies(Permission permission) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((action == null) ? 0 : action.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    SimpleFilePermission other = (SimpleFilePermission)obj;
    if (action == null) {
      if (other.action != null) return false;
    } else if (!action.equals(other.action)) return false;
    return true;
  }

  @Override
  public String getActions() {
    return action;
  }

  public static FileReadPermission createFileRead(String path) {
    return new FileReadPermission(path);
  }

  public static FileWritePermission createFileWrite(String path) {
    return new FileWritePermission(path);
  }
}
