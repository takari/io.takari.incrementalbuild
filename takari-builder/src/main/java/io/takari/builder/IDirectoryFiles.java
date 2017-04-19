package io.takari.builder;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

/**
 * Helper interface to encapsulate inputs selected from a particular directory.
 */
public interface IDirectoryFiles {

  File location();

  Path locationPath();

  Set<String> includes();

  Set<String> excludes();

  Set<File> files();

  Set<Path> filePaths();

  /**
   * Relative file names. Forward slash (i.e. '/') is used as path separator on all platforms.
   */
  Set<String> filenames();

}
