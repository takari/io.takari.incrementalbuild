package io.takari.builder.internal.pathmatcher;

/*
 * Copyright The Codehaus Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;


/**
 * Describes a match target for SelectorUtils.
 * <p/>
 * Significantly more efficient than using strings, since re-evaluation and re-tokenizing is
 * avoided.
 *
 * @author Kristian Rosenvold
 */
class Plexus_MatchPattern {
  private final String source;

  private final String regexPattern;

  private final String[] tokenized;
  private final char[][] tokenizedChar;

  private Plexus_MatchPattern(String source, String separator) {
    regexPattern = Plexus_SelectorUtils.isRegexPrefixedPattern(source)
        ? source.substring(Plexus_SelectorUtils.REGEX_HANDLER_PREFIX.length(),
            source.length() - Plexus_SelectorUtils.PATTERN_HANDLER_SUFFIX.length())
        : null;
    this.source = Plexus_SelectorUtils.isAntPrefixedPattern(source)
        ? source.substring(Plexus_SelectorUtils.ANT_HANDLER_PREFIX.length(),
            source.length() - Plexus_SelectorUtils.PATTERN_HANDLER_SUFFIX.length())
        : source;
    tokenized = tokenizePathToString(this.source, separator);
    tokenizedChar = new char[tokenized.length][];
    for (int i = 0; i < tokenized.length; i++) {
      tokenizedChar[i] = tokenized[i].toCharArray();
    }

  }

  boolean matchPath(String str, char[][] strDirs, boolean isCaseSensitive) {
    if (regexPattern != null) {
      return str.matches(regexPattern);
    } else {
      return Plexus_SelectorUtils.matchAntPathPattern(getTokenizedPathChars(), strDirs,
          isCaseSensitive);
    }
  }

  public char[][] getTokenizedPathChars() {
    return tokenizedChar;
  }

  static String[] tokenizePathToString(String path, String separator) {
    List<String> ret = new ArrayList<String>();
    // using this constructor for StringTokenizer (with returnDelims=true) is substantially more
    // performant than not returning delimiters, even with the overhead of discarding the
    // delimiters ourselves.
    StringTokenizer st = new StringTokenizer(path, separator, true);
    while (st.hasMoreTokens()) {
      String next = getNext(st, separator);
      if (next != null) {
        ret.add(next);
      }
    }
    return ret.toArray(new String[ret.size()]);
  }

  private static String getNext(StringTokenizer st, String separator) {
    String value = st.nextToken();
    if (separator.equals(value))
      value = null;
    else if (st.hasMoreTokens()) st.nextToken();
    return value;
  }

  public static Plexus_MatchPattern fromString(String source) {
    return new Plexus_MatchPattern(source, PathNormalizer.SEPARATOR);
  }

}
