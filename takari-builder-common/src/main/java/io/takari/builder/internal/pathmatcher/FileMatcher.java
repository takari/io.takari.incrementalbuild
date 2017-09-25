package io.takari.builder.internal.pathmatcher;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.StringTokenizer;

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

  static class SinglePathMatcher implements Matcher {

    final String path;

    public SinglePathMatcher(String path) {
      this.path = path;
    }

    @Override
    public boolean matches(String path) {
      return this.path.equals(path);
    }

  }

  static class Trie {
    Map<String, Trie> children;
    Collection<String> includes;

    public void addIncludes(String glob) {
      if (includes == null) {
        includes = new LinkedHashSet<>();
      }
      includes.add(glob);
      if (children != null) {
        children.values().forEach(child -> child.addIncludesTo(includes));
        children = null;
      }
    }

    private void addIncludesTo(Collection<String> other) {
      if (children != null) {
        children.values().forEach(child -> child.addIncludesTo(other));
      }
      if (includes != null) {
        other.addAll(includes);
      }
    }

    public Trie child(String name) {
      if (includes != null) {
        return this;
      }
      if (children == null) {
        children = new HashMap<>();
      }
      Trie child = children.get(name);
      if (child == null) {
        child = new Trie();
        children.put(name, child);
      }
      return child;
    }

    public Map<String, Collection<String>> subdirs() {
      return addSubdirs(null, new HashMap<>());
    }

    private Map<String, Collection<String>> addSubdirs(String path,
        Map<String, Collection<String>> subdirs) {
      if (children != null) {
        children.forEach((name, child) -> child.addSubdirs(childname(path, name), subdirs));
      } else {
        subdirs.put(path, includes);
      }
      return subdirs;
    }

    private static String childname(String basedir, String name) {
      return basedir != null ? basedir + "/" + name : name;
    }
  }

  final Matcher includesMatcher;
  final Matcher excludesMatcher;

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
      if (!basepath.endsWith("/")) {
        gb.append(basepath).append('/');
      }
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
    return matches(toAbsolutePath(file));
  }

  public boolean matches(File file) {
    return matches(file.getAbsolutePath());
  }

  /**
   * Given a directory, returns a map of location to FileMatcher that will optimize the lookup. The
   * key can either be a file (if it is a single path matcher) or a directory (if it is an
   * open-ended matcher). The associated matcher will still be relative to the basedir, not to the
   * key.
   * 
   * @param basedir
   * @param includes
   * @param excludes
   * @return
   */
  public static Map<Path, FileMatcher> subMatchers(final Path basedir, Collection<String> includes,
      Collection<String> excludes) {
    if (includes == null || includes.isEmpty()) {
      return Collections.singletonMap(basedir, absoluteMatcher(basedir, includes, excludes));
    }
    Trie root = new Trie();
    for (String include : includes) {
      // ant shorthand syntax
      if (include.endsWith("/")) {
        include = include + "**"; // chuck norris should approve
      }
      Trie trie = root;
      StringTokenizer st = new StringTokenizer(include, "/");
      while (st.hasMoreTokens()) {
        String name = st.nextToken();
        if (name.contains("*") || name.contains("?")) {
          trie.addIncludes(subglob(name, st));
          break;
        }
        trie = trie.child(name);
      }
    }
    final Matcher excludesMatcher =
        fromStrings(toAbsolutePath(basedir), excludes, MATCH_EVERYTHING);
    Map<Path, FileMatcher> subdirs = new HashMap<>();
    root.subdirs().forEach((relpath, globs) -> {
      Path path = relpath != null ? basedir.resolve(relpath) : basedir;
      FileMatcher matcher = globs != null //
          ? absoluteMatcher(path, globs, excludesMatcher) //
          : singlePathMatcher(path);
      subdirs.put(path, matcher);
    });
    return subdirs;
  }

  private static FileMatcher absoluteMatcher(Path basedir, Collection<String> includes,
      Matcher excludesMatcher) {
    final String basepath = toAbsolutePath(basedir);
    final Matcher includesMatcher = fromStrings(basepath, includes, null);
    return new FileMatcher(toDirectoryPath(basepath), includesMatcher, excludesMatcher);
  }

  private static FileMatcher singlePathMatcher(Path path) {
    return new FileMatcher(null, new SinglePathMatcher(path.toString()) /* includesMatcher */,
        null /* excludesMatcher */);
  }

  private static String subglob(String name, StringTokenizer st) {
    StringBuilder glob = new StringBuilder(name);
    while (st.hasMoreTokens()) {
      glob.append('/').append(st.nextToken());
    }
    return glob.toString();
  }

  public static FileMatcher absoluteMatcher(final Path basedir, Collection<String> includes,
      Collection<String> excludes) {
    final String basepath = toAbsolutePath(basedir);
    final Matcher includesMatcher = fromStrings(basepath, includes, null);
    final Matcher excludesMatcher = fromStrings(basepath, excludes, MATCH_EVERYTHING);
    return new FileMatcher(toDirectoryPath(basepath), includesMatcher, excludesMatcher);
  }

  protected static String toDirectoryPath(final String basepath) {
    return basepath.endsWith("/") ? basepath : basepath + "/";
  }

  private static String toAbsolutePath(final Path path) {
    return path.toAbsolutePath().toString();
  }

}
