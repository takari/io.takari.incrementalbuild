package io.takari.incrementalbuild.spi;

import java.io.File;
import java.util.Collection;

class FileMatcher {

  private final io.takari.builder.internal.pathmatcher.FileMatcher fileMatcher;

  private FileMatcher(io.takari.builder.internal.pathmatcher.FileMatcher fileMatcher) {
    this.fileMatcher = fileMatcher;
  }

  public boolean matches(File file) {
    return fileMatcher.matches(file.toPath());
  }

  public static FileMatcher matcher(final File basedir, Collection<String> includes,
      Collection<String> excludes) {
    return new FileMatcher(io.takari.builder.internal.pathmatcher.FileMatcher
        .matcher(basedir.toPath(), includes, excludes));
  }

  public static FileMatcher absoluteMatcher(final File basedir, Collection<String> includes,
      Collection<String> excludes) {
    return new FileMatcher(io.takari.builder.internal.pathmatcher.FileMatcher
        .absoluteMatcher(basedir.toPath(), includes, excludes));
  }

}
