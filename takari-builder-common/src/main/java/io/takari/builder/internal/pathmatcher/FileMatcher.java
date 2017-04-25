package io.takari.builder.internal.pathmatcher;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;

public class FileMatcher {
  private static interface Matcher {
    public boolean matches(String path);
  }

  private static final Matcher MATCH_EVERYTHING = new Matcher() {
    @Override
    public boolean matches(String path) {
      return true;
    }
  };

  private final Matcher includesMatcher;
  private final Matcher excludesMatcher;

  protected final String basedir;

  private static Matcher fromStrings(String basepath, Collection<String> globs,
      Matcher everything) {
    if (globs == null || globs.isEmpty()) {
      return null; // default behaviour appropriate for includes/excludes pattern
    }
    final ArrayList<String> normalized = new ArrayList<>();
    for (String glob : globs) {
      if ("*".equals(glob) || "**".equals(glob) || "**/*".equals(glob)) {
        return everything; // matches everything
      }

      StringBuilder gb = new StringBuilder();
      gb.append(basepath).append('/');
      gb.append(glob.startsWith("/") ? glob.substring(1) : glob);

      // from https://ant.apache.org/manual/dirtasks.html#patterns
      // There is one "shorthand": if a pattern ends with / or \, then ** is appended
      if (glob.endsWith("/")) {
        gb.append("**");
      }
      normalized.add(gb.toString().replace('/', File.separatorChar));
    }
    final Plexus_MatchPatterns matcher = Plexus_MatchPatterns.from(normalized);
    return new Matcher() {
      @Override
      public boolean matches(String path) {
        return matcher.matches(path, false);
      }
    };
  }

  public boolean matches(String path) {
    if (basedir != null && !path.startsWith(basedir)) {
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

  private FileMatcher(String basedir, Matcher includesMatcher, Matcher excludesMatcher) {
    this.basedir = basedir;
    this.includesMatcher = includesMatcher;
    this.excludesMatcher = excludesMatcher;
  }

  public boolean matches(Path file) {
    return matches(file.toAbsolutePath().toString());
  }

  public boolean matches(File file) {
    return matches(file.getAbsolutePath());
  }

  public static FileMatcher absoluteMatcher(final Path basedir, Collection<String> includes,
      Collection<String> excludes) {
    final String basepath = basedir.toAbsolutePath().toString();
    final Matcher includesMatcher = fromStrings(basepath, includes, null);
    final Matcher excludesMatcher = fromStrings(basepath, excludes, MATCH_EVERYTHING);
    return new FileMatcher(basepath.endsWith(File.separator) ? basepath : basedir + File.separator,
        includesMatcher, excludesMatcher);
  }

}
