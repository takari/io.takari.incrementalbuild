package io.takari.incrementalbuild.spi;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;

// XXX rework using plexus MatchPatterns, it is significantly faster than java nio FS
class PathMatchers {

  private static PathMatcher fromStrings(FileSystem fs, Collection<String> globs) {
    if (globs == null || globs.isEmpty()) {
      return null; // matches everything
    }
    final ArrayList<PathMatcher> matchers = new ArrayList<>();
    for (String glob : globs) {
      if ("*".equals(glob) || "**".equals(glob) || "**/*".equals(glob)) {
        return null; // matches everything
      }
      StringBuilder gb = new StringBuilder("glob:");
      if (!glob.startsWith("**")) {
        gb.append("**/");
      }
      gb.append(glob);
      matchers.add(fs.getPathMatcher(gb.toString()));
    }
    return new PathMatcher() {
      @Override
      public boolean matches(Path path) {
        for (PathMatcher matcher : matchers) {
          if (matcher.matches(path)) {
            return true;
          }
        }
        return false;
      }
    };
  }

  /**
   * Matches paths using includes/excludes globs.
   */
  public static PathMatcher relativeMatcher(final Path basepath, Collection<String> includes,
      Collection<String> excludes) {
    final FileSystem fs = basepath.getFileSystem();
    final PathMatcher includesMatcher = fromStrings(fs, includes);
    final PathMatcher excludesMatcher = fromStrings(fs, excludes);
    return new PathMatcher() {
      @Override
      public boolean matches(Path path) {
        if (excludesMatcher != null && excludesMatcher.matches(path)) {
          return false;
        }
        if (includesMatcher != null) {
          return includesMatcher.matches(path);
        }
        return true;
      }
    };
  }

  /**
   * Matches paths using basepath and includes/excludes globs.
   * <p>
   * Paths that are not children of basepath do not match.
   */
  public static PathMatcher absoluteMatcher(final Path basepath, Collection<String> includes,
      Collection<String> excludes) {
    final PathMatcher includesMatcher = fromStrings(basepath.getFileSystem(), includes);
    final PathMatcher excludesMatcher = fromStrings(basepath.getFileSystem(), excludes);
    return new PathMatcher() {
      @Override
      public boolean matches(Path path) {
        if (!path.startsWith(basepath)) {
          return false;
        }
        if (excludesMatcher != null && excludesMatcher.matches(path)) {
          return false;
        }
        if (includesMatcher != null) {
          return includesMatcher.matches(path);
        }
        return true;
      }
    };
  }

}
