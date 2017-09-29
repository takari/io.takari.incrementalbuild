package io.takari.builder.internal.pathmatcher;

import java.io.StringWriter;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Encapsulates a set of include/exclude path matching rules. Each rule can match either specific
 * path or a path prefix. Only absolute paths are supported and the implementation generally assumes
 * the paths are normalized by {@link PathNormalizer}.
 * 
 * <a href="https://en.wikipedia.org/wiki/Trie">Prefix tree</a> is used to represent the matching
 * rules internally. Matching performance only depends on number segments in the path being matched,
 * does not depend on number of matching rules.
 */
public class PathMatcher {

  private static final char SEPARATOR_CHAR = '/';
  private static final String SEPARATOR = "/";

  // represents "path rule" node among children of the "path prefix" node with the same path
  private static final String EMPTY = "";

  private static enum MatchMode {
    include, exclude, inherit;
  }

  private static class Node {

    // creates new "path prefix" match rule node with specified children and match mode
    public Node(Map<String, Node> children, MatchMode matchMode) {
      assert children != null;
      this.children = children;
      this.matchMode = matchMode;
    }

    // creates new "path" match rule node with specified match mode
    public Node(MatchMode matchMode) {
      assert matchMode != MatchMode.inherit;
      this.children = null;
      this.matchMode = matchMode;
    }

    public final Map<String, Node> children;
    public final MatchMode matchMode;
  }

  /**
   * The root of a unix like file system, has implied path of {@value #SEPARATOR}.
   */
  private final Node root;

  private PathMatcher(Node root) {
    this.root = root;
  }

  /**
   * Returns {@code true} if the given path is matched by "includes" rule or {@code false} if the
   * path is matched by "excludes" rule.
   * 
   * @throws IllegalArgumentException if the given path is not match by any matcher rule.
   */
  public boolean includes(String path) throws IllegalArgumentException {
    return match(path, null).matchMode == MatchMode.include;
  }

  public String getMatchingRule(String path) throws IllegalArgumentException {
    List<Map.Entry<String, Node>> trail = new ArrayList<>();
    Node node = match(path, e -> trail.add(e));

    StringBuilder rule = new StringBuilder();
    rule.append(node.matchMode == MatchMode.include ? "+" : "-");
    for (Map.Entry<String, Node> entry : trail) {
      rule.append("/").append(entry.getKey());
      if (entry.getValue() == node) {
        break;
      }
    }
    if (node.children != null) {
      rule.append("/**");
    }

    return rule.toString();
  }

  /**
   * Returns the most specific rule node for the given path.
   */
  private Node match(String path, Consumer<Map.Entry<String, Node>> trail) {
    Node rule = match0(path, trail);
    if (rule.matchMode == MatchMode.inherit) {
      throw new IllegalArgumentException("No rule matches path " + path);
    }
    return rule;
  }

  private Node match0(String path, Consumer<Map.Entry<String, Node>> trail) {
    if (path == null || path.isEmpty()) {
      throw new NullPointerException();
    }

    if (path.charAt(0) != SEPARATOR_CHAR) {
      throw new IllegalArgumentException("Path is not absolute " + path);
    }

    Node node = root, inherited = root;
    for (String element : split(path)) {
      node = node.children.get(element);
      if (node == null) {
        return inherited;
      }
      if (trail != null) {
        trail.accept(new SimpleImmutableEntry<>(element, node));
      }
      if (node.matchMode != MatchMode.inherit) {
        inherited = node;
      }
    }

    node = node.children.get(EMPTY);
    return node != null ? node : inherited;
  }

  // TODO inline, this creates unnecessary temporary objects
  static Iterable<String> split(String path) {
    ArrayList<String> split = new ArrayList<>();
    for (String element : path.split(SEPARATOR)) {
      if (element.isEmpty()) {
        continue;
      }
      if ("..".equals(element)) {
        if (!split.isEmpty()) {
          split.remove(split.size() - 1);
        }
        continue;
      }
      if (".".equals(element)) {
        continue;
      }
      split.add(element);
    }
    return split;
  }

  //
  // Traversal
  //

  /**
   * Visitor for path matching rules
   */
  public static interface RuleVisitor {

    /**
     * Visit the given path matching rule
     * 
     * @param includes is {@code true} for includes rules, {@code false} for excludes rules
     * @param path is the rule path, prefix rule paths end with '/' separator char
     */
    void visit(boolean includes, String path);
  }

  public void traverse(RuleVisitor visitor) {
    traverse(new NodeVisitor() {
      final StringBuilder path = new StringBuilder();

      @Override
      public void enterPrefix(String name, Node node) {
        path.append(name).append(SEPARATOR);
        if (node.matchMode != MatchMode.inherit) {
          visitor.visit(node.matchMode == MatchMode.include, path.toString());
        }
      }

      @Override
      public void visitPath(String name, Node node) {
        assert node.matchMode != MatchMode.inherit;

        visitor.visit(node.matchMode == MatchMode.include, path.toString() + name);
      }

      @Override
      public void leavePrefix(String name, Node node) {
        path.setLength(path.length() - name.length() - 1);
      }
    });
  }

  private static interface NodeVisitor {

    /**
     * Called when traversal enters the given prefix node.
     * 
     * @param the node name in the context of the parent node. {@value #SEPARATOR} for the root
     *        node.
     */
    void enterPrefix(String name, Node node);

    /**
     * Called when traversal visits the given path node.
     * 
     * @param the node name in the context of the parent node. {@value #SEPARATOR} for the root
     *        node.
     */
    void visitPath(String name, Node node);

    /**
     * Called when traversal leaves the given prefix node.
     * 
     * @param the node name in the context of the parent node. {@value #SEPARATOR} for the root
     *        node.
     */
    void leavePrefix(String name, Node node);
  }

  private void traverse(NodeVisitor visit) {
    traverse(visit, EMPTY, root);
  }

  private void traverse(NodeVisitor visitor, String name, Node node) {
    Node pathNode = node.children.get(EMPTY);
    if (pathNode != null) {
      visitor.visitPath(name, pathNode);
    }
    visitor.enterPrefix(name, node);
    node.children.forEach((childName, child) -> {
      if (!EMPTY.equals(childName)) {
        traverse(visitor, childName, child);
      }
    });
    visitor.leavePrefix(name, node);
  }

  @Override
  public String toString() {
    final StringWriter writer = new StringWriter();
    traverse((includes, path) -> {
      writer.write(includes ? "+" : "-");
      writer.write(path.toString());
      writer.write("\n");
    });
    return writer.toString();
  }

  //
  // Construction
  //

  private static class BuilderNode {
    public final Map<String, BuilderNode> children;
    public MatchMode matchMode;

    // new directory node
    public BuilderNode() {
      this.children = new HashMap<>();
      this.matchMode = MatchMode.inherit;
    }

    // new file node
    public BuilderNode(MatchMode matchMode) {
      this.children = null;
      this.matchMode = matchMode;
    }

    public Node toNode() {
      if (children == null) {
        return new Node(matchMode);
      }
      Map<String, Node> children = new HashMap<>();
      for (Map.Entry<String, BuilderNode> entry : this.children.entrySet()) {
        children.put(entry.getKey(), entry.getValue().toNode());
      }
      return new Node(children, matchMode);
    }
  }

  public static class Builder {
    private BuilderNode root;

    Builder(MatchMode matchMode) {
      this.root = new BuilderNode();
      this.root.matchMode = matchMode;
    }

    // returns directory node with the given path
    // creates the requested node and any intermediate nodes if necessary
    BuilderNode getDirectoryNode(String path) {
      BuilderNode node = root;
      for (String element : split(path)) {
        node = getDirectoryNode(node, element);
      }
      return node;
    }

    BuilderNode getDirectoryNode(BuilderNode parent, String name) {
      BuilderNode child = parent.children.get(name);
      if (child == null) {
        child = new BuilderNode(); // "directory" node
        parent.children.put(name, child);
      }
      return child;
    }

    /**
     * Adds all rules from the given matcher.
     */
    public Builder addMatcher(PathMatcher matcher) {
      matcher.traverse(new NodeVisitor() {
        Deque<BuilderNode> stack = new ArrayDeque<>();

        @Override
        public void enterPrefix(String name, Node node) {
          BuilderNode builderNode;
          if (stack.isEmpty()) {
            assert EMPTY.equals(name);
            builderNode = root;
          } else {
            builderNode = getDirectoryNode(stack.peek(), name);
          }
          if (node.matchMode != MatchMode.inherit) {
            builderNode.matchMode = node.matchMode;
          }
          stack.push(builderNode);
        }

        @Override
        public void visitPath(String name, Node node) {
          BuilderNode builderNode = new BuilderNode(node.matchMode);
          getDirectoryNode(stack.peek(), name).children.put(EMPTY, builderNode);
        }

        @Override
        public void leavePrefix(String name, Node node) {
          stack.remove();
        }
      });

      return this;
    }

    public Builder includeRoot() {
      root.matchMode = MatchMode.include;

      return this;
    }

    public Builder excludeRoot() {
      root.matchMode = MatchMode.exclude;

      return this;
    }

    public Builder includePrefix(String path) {
      getDirectoryNode(path).matchMode = MatchMode.include;

      return this;
    }

    public Builder excludePrefix(String path) {
      getDirectoryNode(path).matchMode = MatchMode.exclude;

      return this;
    }

    public Builder includePath(String path) {
      getDirectoryNode(path).children.put(EMPTY, new BuilderNode(MatchMode.include));

      return this;
    }

    public Builder excludePath(String path) {
      getDirectoryNode(path).children.put(EMPTY, new BuilderNode(MatchMode.exclude));

      return this;
    }

    public PathMatcher build() {
      BuilderNode root = this.root;
      if (root == null) {
        root = new BuilderNode();
      }
      return new PathMatcher(root.toNode());
    }
  }

  private static class NormalizedBuilder extends Builder {

    private PathNormalizer normalizer;

    NormalizedBuilder(PathNormalizer normalizer, MatchMode matchMode) {
      super(matchMode);
      this.normalizer = normalizer;
    }

    @Override
    BuilderNode getDirectoryNode(String path) {
      return super.getDirectoryNode(normalizer.normalize(path));
    }
  }

  /**
   * Creates and returns new path matcher builder. Paths added to the builder will be normalized
   * using provided {@code normalizer}.
   */
  public static Builder builder(PathNormalizer normalizer) {
    return new NormalizedBuilder(normalizer, MatchMode.inherit);
  }

  /**
   * Creates and returns new matcher builder. Paths added to the builder are assumed to be
   * normalized by a {@link PathNormalizer}, but the builder itself will not normalize paths.
   */
  public static Builder builder() {
    return new Builder(MatchMode.inherit);
  }

}
