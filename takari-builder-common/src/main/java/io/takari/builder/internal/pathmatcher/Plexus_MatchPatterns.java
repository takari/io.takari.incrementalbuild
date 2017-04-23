package io.takari.builder.internal.pathmatcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A list of patterns to be matched
 *
 * @author Kristian Rosenvold
 */
class Plexus_MatchPatterns {
  private final Plexus_MatchPattern[] patterns;

  private Plexus_MatchPatterns(Plexus_MatchPattern[] patterns) {
    this.patterns = patterns;
  }

  /**
   * Checks these MatchPatterns against a specified string.
   * <p/>
   * Uses far less string tokenization than any of the alternatives.
   *
   * @param name The name to look for
   * @param isCaseSensitive If the comparison is case sensitive
   * @return true if any of the supplied patterns match
   */
  public boolean matches(String name, boolean isCaseSensitive) {
    String[] tokenized = Plexus_MatchPattern.tokenizePathToString(name, File.separator);
    return matches(name, tokenized, isCaseSensitive);
  }

  public boolean matches(String name, String[] tokenizedName, boolean isCaseSensitive) {
    char[][] tokenizedNameChar = new char[tokenizedName.length][];
    for (int i = 0; i < tokenizedName.length; i++) {
      tokenizedNameChar[i] = tokenizedName[i].toCharArray();
    }
    for (Plexus_MatchPattern pattern : patterns) {
      if (pattern.matchPath(name, tokenizedNameChar, isCaseSensitive)) {
        return true;
      }
    }
    return false;
  }

  public static Plexus_MatchPatterns from(Iterable<String> strings) {
    return new Plexus_MatchPatterns(getMatchPatterns(strings));
  }

  private static Plexus_MatchPattern[] getMatchPatterns(Iterable<String> items) {
    List<Plexus_MatchPattern> result = new ArrayList<Plexus_MatchPattern>();
    for (String string : items) {
      result.add(Plexus_MatchPattern.fromString(string));
    }
    return result.toArray(new Plexus_MatchPattern[result.size()]);
  }

}
