package io.takari.incrementalbuild.spi;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;

// XXX rework using plexus MatchPatterns, it is significantly faster than java nio FS
class PathMatchers {

  private static final PathMatcher MATCH_EVERYTHING = new PathMatcher() {
    @Override
    public boolean matches(Path path) {
      return true;
    }
  };

  private static PathMatcher fromStrings(FileSystem fs, Collection<String> globs,
      PathMatcher everything) {
    if (globs == null || globs.isEmpty()) {
      return null; // default behaviour appropriate for includes/excludes pattern
    }
    final ArrayList<PathMatcher> matchers = new ArrayList<>();
    for (String glob : globs) {
      if ("*".equals(glob) || "**".equals(glob) || "**/*".equals(glob)) {
        return everything; // matches everything
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
    final PathMatcher includesMatcher = fromStrings(fs, includes, null);
    final PathMatcher excludesMatcher = fromStrings(fs, excludes, MATCH_EVERYTHING);
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
    final PathMatcher includesMatcher = fromStrings(basepath.getFileSystem(), includes, null);
    final PathMatcher excludesMatcher =
        fromStrings(basepath.getFileSystem(), excludes, MATCH_EVERYTHING);
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
