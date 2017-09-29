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

  private static final char SEPARATOR_CHAR = '/';

  private final String basedir; // no trailing slash

  private PathNormalizer(Path basedir) {
    this.basedir = normalize0(basedir);
  }

  public static PathNormalizer create(Path basedir) {
    return new PathNormalizer(basedir);
  }

  public String getBasedir() {
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
      return file;
    }
    return normalize0(file);
  }

  /**
   * Returns {@code true} if {@code file} equals to {@link #basedir} or one of files/directories
   * under basedir.
   */
  boolean isBasedirOrNestedFile(String file) {
    if (file.startsWith(basedir)) {
      int baseLength = basedir.length();
      return baseLength == file.length() || file.charAt(baseLength) == SEPARATOR_CHAR;
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
      return file;
    }
    return normalize0(path);
  }

  /**
   * Returns canonical file path with '/' separator char on all platforms. Returns absolute file
   * path if canonical path cannot be determined due to IOException.
   */
  public static String normalize0(Path file) {
    String normalized;
    try {
      normalized = file.toFile().getCanonicalPath();
    } catch (IOException e) {
      normalized = file.toAbsolutePath().toString();
    }
    return normalized;
  }

  /**
   * @see #normalize0(File)
   */
  public static String normalize0(String file) {
    return normalize0(Paths.get(file));
  }

}
