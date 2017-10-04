package io.takari.builder.internal.pathmatcher;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Filesystem path normalizer. Normalized paths use '/' separator on all platforms and are absolute,
 * i.e. start with '/'.
 */
public class PathNormalizer {

  static final char SEPARATOR_CHAR = '/';
  private static final boolean FIXFS = File.separatorChar != SEPARATOR_CHAR;
  static final String SEPARATOR = "/";

  /**
   * Platform-dependent canonical path without trailing file separatorChar (i.e. on *nix
   * {@code /some/directory} and on Windows {@code C:\some\directory}).
   * 
   * {@code null} means "no normalization base directory" and forces all paths to be normalized
   * using more expensive {@link #normalize0(Path)}.
   */
  private final String basedir;

  private PathNormalizer(String basedir) {
    this.basedir = basedir;
  }

  /**
   * Creates {@link PathNormalizer} optimized for use with {@link PathMatcher}.
   * 
   * Paths under the provided {@code basedir} are not normalized using filesystem I/O calls (i.e.
   * {@link File#getCanonicalFile()} or similar) and may contain '../' and './' special directories
   * after normalization.
   */
  public static PathNormalizer createNormalizer(Path basedir) {
    return new PathNormalizer(toCanonicalPath(basedir));
  }

  /**
   * Creates {@link PathNormalizer} using value returned by {@link #getMemento()}
   */
  public static PathNormalizer createNormalizer(String memento) {
    return new PathNormalizer(memento);
  }

  /**
   * Creates {@link PathNormalizer} that uses filesystem I/O calls (i.e.
   * {@link File#getCanonicalFile()} or similar) to normalize all paths.
   */
  public static PathNormalizer createNormalizer() {
    return new PathNormalizer(null);
  }

  /**
   * Returns memento string which can be passed to {@link #createNormalizer(String)}. The main
   * usecase is to recreate {@link PathNormalizer} in another JVM running on the same system.
   */
  public String getMemento() {
    if (basedir == null) {
      // existing clients do not call this method for non-optimized normalizer
      // so lets fail for now and decide what to do when we have a usecase
      throw new IllegalStateException();
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
    if (basedir != null && file.startsWith(basedir)) {
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
    String canonicalPath = toCanonicalPath(file);
    if (FIXFS) {
      return fixfs(canonicalPath);
    }
    return canonicalPath;
  }

  /**
   * Like {@link #getCanonicalPath(Path)}, but returns String.
   */
  private static String toCanonicalPath(Path file) {
    try {
      // note that Path#toRealPath() only works for existing files
      return file.toFile().getCanonicalPath();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Path getCanonicalPath(Path file) {
    try {
      // note that Path#toRealPath() only works for existing files
      return file.toFile().getCanonicalFile().toPath();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * @see #normalize0(File)
   */
  public static String normalize0(String file) {
    return normalize0(toPath(file));
  }

  public static Path toPath(String file) {
    if (FIXFS) {
      // on windows FilePermission path is \C:\path\to\file
      // and some code uses URL.getPath(), which results in /C:/path/to/file
      // both forms are accepted by File(String) constructor, but not Paths.get(String)
      return new File(file).toPath();
    }
    return Paths.get(file);
  }
}
