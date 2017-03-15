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

  private final String basedir;
  private final String basepath; // no trailing slash

  public PathNormalizer(Path basedir) {
    this.basepath = normalize0(basedir);
    this.basedir = this.basepath + SEPARATOR_CHAR;
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
    file = Paths.get(file).normalize().toString();
    if (file.startsWith(basedir) || basepath.equals(file)) {
      // avoid expensive normalize0 call if file starts with basedir prefix.
      // this is possible because basedir is normalized in the constructor and PatchMatcher
      // resolves ../ and ./ special directory names.
      return file;
    }
    return normalize0(file);
  }

  /**
   * File path normalization implementation optimized for use with {@link PathMatcher}.
   * 
   * Normalized path may include '../' and './' special directory names, which are resolved by
   * {@link PathMatcher}.
   */
  public String normalize(Path file) {
    String path = file.toAbsolutePath().normalize().toString();
    if (file.toAbsolutePath().startsWith(basedir)
        || basepath.equals(file.toAbsolutePath().toString())) {
      // avoid expensive normalize0 call if file starts with basedir prefix.
      // this is possible because basedir is normalized in the constructor and PatchMatcher
      // resolves ../ and ./ special directory names.
      return path;
    }
    return normalize0(file);
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
