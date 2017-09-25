package io.takari.builder.internal.pathmatcher;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;

/**
 * A trie whose nodes represent path segments of jar entries
 * 
 * @author jaime.morales
 *
 */
public class JarEntries {

  private static class Node {
    private final Map<String, Node> children;
    private final boolean isFile;
    private final String path;

    private Node(String path, boolean isFile) {
      this.path = path;
      this.isFile = isFile;
      children = isFile ? null : new HashMap<>();
    }

    private List<Path> matchFiles(FileMatcher matcher) {
      if (isFile) {
        if (matcher.matches("/" + path)) {
          return Arrays.asList(Paths.get(path));
        } else {
          return Collections.emptyList();
        }
      }

      List<Path> paths = new ArrayList<>();

      matchFiles(matcher, paths);

      return paths;
    }

    private void matchFiles(FileMatcher matcher, List<Path> paths) {
      if (children == null) {
        return;
      }
      for (Node node : children.values()) {
        if (node.isFile) {
          if (matcher.matches("/" + node.path)) {
            paths.add(Paths.get(node.path));
          }
        } else {
          node.matchFiles(matcher, paths);
        }
      }
    }
  }

  private final Node root = new Node("", false);

  public JarEntries(Enumeration<JarEntry> entries) {
    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();
      if (!entry.isDirectory()) {
        addEntry(entry.getName());
      }
    }
  }

  private void addEntry(String entry) {
    String[] segments = Plexus_MatchPattern.tokenizePathToString(entry, File.separator);
    int index = 0, len = segments.length;
    Node node = root;
    String segment;

    while (index < len) {
      segment = segments[index];
      Map<String, Node> children = node.children;
      node = children.get(segment);

      if (node == null) {
        String currentPath = getCurrentPath(segments, index);
        boolean isFile = currentPath.equals(entry);
        node = new Node(currentPath, isFile);
        children.put(segment, node);
      }
      index++;
    }
  }

  private String getCurrentPath(String[] segments, int index) {
    return String.join("/", Arrays.copyOfRange(segments, 0, index + 1));
  }

  private Node getNode(Path path) {
    String[] segments = Plexus_MatchPattern.tokenizePathToString(path.toString(), File.separator);
    int index = 0, len = segments.length;
    Node node = root;
    String segment;

    while (index < len) {
      segment = segments[index];
      Map<String, Node> children = node.children;
      node = children.get(segment);
      if (node == null) {
        return null;
      }
      index++;
    }
    return node;
  }

  public List<Path> match(Map<Path, FileMatcher> subdirMatchers) {
    List<Path> matchedPaths = new ArrayList<>();

    for (Map.Entry<Path, FileMatcher> entry : subdirMatchers.entrySet()) {
      Node node = getNode(entry.getKey());

      if (node == null) {
        continue;
      }

      matchedPaths.addAll(node.matchFiles(entry.getValue()));
    }

    return matchedPaths;
  }
}
