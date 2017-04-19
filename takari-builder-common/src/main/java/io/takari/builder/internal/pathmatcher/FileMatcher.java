package io.takari.builder.internal.pathmatcher;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;

public class FileMatcher extends AbstractMatcher {

  private FileMatcher(String basedir, Matcher includesMatcher, Matcher excludesMatcher) {
    super(basedir, includesMatcher, excludesMatcher);
  }

  public boolean matches(Path file) {
    return matches(file.toAbsolutePath().toString());
  }

  public static FileMatcher matcher(final Path basedir, Collection<String> includes,
      Collection<String> excludes) {
    final String basepath = basedir.toAbsolutePath().toString();
    final Matcher includesMatcher = fromStrings(basepath, includes, null);
    final Matcher excludesMatcher = fromStrings(basepath, excludes, MATCH_EVERYTHING);
    return new FileMatcher(null, includesMatcher, excludesMatcher);
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
