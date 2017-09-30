package io.takari.builder.internal.pathmatcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Filesystem path normalizer.
 * 
 * Normalized paths use '/' separator on all platforms and are absolute, i.e. start with '/'.
 * 
 * To minimize number of expensive {@link File#getCanonicalFile()} calls, the normalizer is aware of
 * the build base directory. Normalized paths under the base directory may contain '../' and './'
 * special directories by default.
 */
public class PathNormalizer {

  static final char SEPARATOR_CHAR = '/';
  private static final boolean FIXFS = File.separatorChar != SEPARATOR_CHAR;
  static final String SEPARATOR = "/";

  private final String basedir; // canonical path without trailing file separatorChar

  private PathNormalizer(String basedir) {
    this.basedir = basedir;
  }

  public static PathNormalizer create(Path basedir) {
    return new PathNormalizer(getCanonicalPath(basedir));
  }

  public static PathNormalizer createFSRoot() {
    return new PathNormalizer(SEPARATOR);
  }

  public String getBasedir() {
    if (FIXFS) {
      return fixfs(basedir);
    }
    return basedir;
  }

  /**
   * File path normalization implementation optimized for use with {@link PathMatcher}.
   * 
   * Normalized path may include '../' and './' special directory names, which are resolved by
   * {@link PathMatcher}.
   */
  public String normalize(String file) {
    if (isBasedirOrNestedFile(file)) {
      // avoid expensive normalize0 call if file starts with basedir prefix.
      // this is possible because basedir is normalized in the constructor and PatchMatcher
      // resolves ../ and ./ special directory names.
      return FIXFS ? fixfs(file) : file;
    }
    return normalize0(file);
  }

  private static String fixfs(String file) {
    return SEPARATOR_CHAR + file.replace(File.separatorChar, SEPARATOR_CHAR);
  }

  /**
   * Returns {@code true} if {@code file} equals to {@link #basedir} or one of files/directories
   * under basedir.
   */
  boolean isBasedirOrNestedFile(String file) {
    if (file.startsWith(basedir)) {
      int baseLength = basedir.length();
      if (baseLength == file.length()) {
        return true;
      }
      char charL = file.charAt(baseLength);
      return charL == SEPARATOR_CHAR || charL == File.separatorChar;
    }
    return false;
  }

  /**
   * File path normalization implementation optimized for use with {@link PathMatcher}.
   * 
   * Normalized path may include '../' and './' special directory names, which are resolved by
   * {@link PathMatcher}.
   */
  public String normalize(Path path) {
    String file = path.toString();
    if (isBasedirOrNestedFile(file)) {
      // avoid expensive normalize0 call if file starts with basedir prefix.
      // this is possible because basedir is normalized in the constructor and PatchMatcher
      // resolves ../ and ./ special directory names.
      return FIXFS ? fixfs(file) : file;
    }
    return normalize0(path);
  }

  /**
   * Returns canonical normalized file path, which on all platforms means:
   * <ul>
   * <li>the path is resolved similarly to {@link File#getCanonicalPath()}
   * <li>the path starts with '/'
   * <li>the path uses '/' file separator char
   * </ul>
   */
  public static String normalize0(Path file) {
    String canonicalPath = getCanonicalPath(file);
    if (FIXFS) {
      return fixfs(canonicalPath);
    }
    return canonicalPath;
  }

  private static String getCanonicalPath(Path file) {
    try {
      return file.toFile().getCanonicalPath();
    } catch (IOException e) {
      return file.toAbsolutePath().toString();
    }
  }

  /**
   * @see #normalize0(File)
   */
  public static String normalize0(String file) {
    if (FIXFS) {
      // on windows FilePermission path is \C:\path\to\file
      // and some code uses URL.getPath(), which results in /C:/path/to/file
      // both forms are accepted by File(String) constructor, but not Paths.get(String)
      return normalize0(new File(file).toPath());
    }
    return normalize0(Paths.get(file));
  }

}
