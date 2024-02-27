/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.pathmatcher;

import static io.takari.builder.internal.pathmatcher.PathNormalizer.normalize0;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Function;

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

        private Map<String, Collection<String>> addSubdirs(String path, Map<String, Collection<String>> subdirs) {
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

    private final String basedir;

    private static Matcher fromStrings(String basepath, Collection<String> globs, Matcher everything) {
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
            normalized.add(gb.toString().replace(File.separatorChar, PathNormalizer.SEPARATOR_CHAR));
        }
        final Plexus_MatchPatterns matcher = Plexus_MatchPatterns.from(normalized);
        return new Matcher() {
            @Override
            public boolean matches(String path) {
                return matcher.matches(path, false);
            }
        };
    }

    private FileMatcher(String basedir, Matcher includesMatcher, Matcher excludesMatcher) {
        this.basedir = basedir;
        this.includesMatcher = includesMatcher;
        this.excludesMatcher = excludesMatcher;
    }

    /**
     * Returns {@code true} if provided path is under this matcher's basedir and satisfies
     * includes/excludes patterns. The provided path is assumed to be normalized according to
     * {@link PathNormalizer#normalize0(Path)}.
     */
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

    public boolean matches(Path file) {
        return matches(normalize0(file));
    }

    public boolean matches(File file) {
        return matches(normalize0(file.toPath()));
    }

    /**
     * Given a directory, returns a map of location to FileMatcher that will optimize the lookup. The
     * key can either be a file (if it is a single path matcher) or a directory (if it is an
     * open-ended matcher). The associated matcher will still be relative to the basedir, not to the
     * key, but will only match paths that start with the key.
     */
    public static Map<Path, FileMatcher> createMatchers(
            final Path basedir, Collection<String> includes, Collection<String> excludes) {
        String basepath = normalize0(basedir);
        if (includes == null || includes.isEmpty()) {
            return Collections.singletonMap(basedir, createMatcher(basepath, includes, excludes));
        }
        return createMatchers(basepath, includes, excludes, PathNormalizer::toPath);
    }

    /**
     * Returns a map of location to FileMatcher that will optimize the lookup. The key is a path of a
     * file or directory in a logical filesystem that uses '/' as file separator. The associated
     * matcher is relative to the root of the logical filesystem, not to the ley, but will only match
     * paths that start with the key.
     */
    public static Map<String, FileMatcher> createMatchers(Collection<String> includes, Collection<String> excludes) {
        String basepath = "";
        if (includes == null || includes.isEmpty()) {
            return Collections.singletonMap(basepath, createMatcher(basepath, includes, excludes));
        }
        return createMatchers(basepath, includes, excludes, Function.identity());
    }

    private static <T> Map<T, FileMatcher> createMatchers(
            String basepath, Collection<String> includes, Collection<String> excludes, Function<String, T> fromString) {
        final Matcher excludesMatcher = fromStrings(basepath, excludes, MATCH_EVERYTHING);
        Map<T, FileMatcher> matchers = new HashMap<>();
        newIncludesTrie(includes).subdirs().forEach((relpath, globs) -> {
            String path = relpath != null ? basepath + "/" + relpath : basepath;
            FileMatcher matcher = globs != null //
                    ? createMatcher(path, globs, excludesMatcher) //
                    : createSinglePathMatcher(path);
            matchers.put(fromString.apply(path), matcher);
        });
        return matchers;
    }

    private static Trie newIncludesTrie(Collection<String> includes) {
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
        return root;
    }

    private static FileMatcher createMatcher(String basedir, Collection<String> includes, Matcher excludesMatcher) {
        final Matcher includesMatcher = fromStrings(basedir, includes, null);
        return new FileMatcher(toDirectoryPath(basedir), includesMatcher, excludesMatcher);
    }

    private static FileMatcher createSinglePathMatcher(String path) {
        return new FileMatcher(null, new SinglePathMatcher(path) /* includesMatcher */, null /* excludesMatcher */);
    }

    private static String subglob(String name, StringTokenizer st) {
        StringBuilder glob = new StringBuilder(name);
        while (st.hasMoreTokens()) {
            glob.append('/').append(st.nextToken());
        }
        return glob.toString();
    }

    /**
     * Creates and returns new matcher for files under specified {@code basedir} that satisfy
     * specified includes/excludes patterns.
     */
    public static FileMatcher createMatcher(
            final Path basedir, Collection<String> includes, Collection<String> excludes) {
        return createMatcher(normalize0(basedir), includes, excludes);
    }

    public static FileMatcher createMatcher(Collection<String> includes, Collection<String> excludes) {
        return createMatcher("", includes, excludes);
    }

    private static FileMatcher createMatcher(
            final String basepath, Collection<String> includes, Collection<String> excludes) {
        final Matcher includesMatcher = fromStrings(basepath, includes, null);
        final Matcher excludesMatcher = fromStrings(basepath, excludes, MATCH_EVERYTHING);
        return new FileMatcher(toDirectoryPath(basepath), includesMatcher, excludesMatcher);
    }

    protected static String toDirectoryPath(final String basepath) {
        return basepath.endsWith("/") ? basepath : basepath + "/";
    }
}
