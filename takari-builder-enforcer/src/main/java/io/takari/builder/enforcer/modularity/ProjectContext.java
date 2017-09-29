package io.takari.builder.enforcer.modularity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import io.takari.builder.enforcer.internal.EnforcerViolation;
import io.takari.builder.internal.pathmatcher.PathMatcher;
import io.takari.builder.internal.pathmatcher.PathMatcher.Builder;
import io.takari.builder.internal.pathmatcher.PathNormalizer;

/**
 * This class contains a list of read and write excludes that must be fully serializable, and should
 * not reference any part of the maven model of the project structure. It will serialize itself
 * through {@link ProjectContext#store(OutputStream)}, and must be loaded, usually by a forked
 * process, in the static method {@link ProjectContext#load(InputStream)}
 *
 * @author igor fed
 * @author rex.hoffman
 */
public class ProjectContext {
  private final String id;

  private final PathNormalizer normalizer;
  private final PathMatcher readMatcher;
  private final PathMatcher writeMatcher;
  private final Set<String> execIncludes;

  // mutable context state (below) can be accessed by multiple threads

  private final Set<EnforcerViolation> violations = ConcurrentHashMap.newKeySet();

  public ProjectContext(PathNormalizer normalizer, String id, PathMatcher readMatcher,
      PathMatcher writeMatcher, Set<String> execIncludes) {
    this.normalizer = normalizer;
    this.id = id;
    this.readMatcher = readMatcher;
    this.writeMatcher = writeMatcher;
    this.execIncludes = execIncludes;
  }

  public final boolean checkRead(String file) {
    return readMatcher.includes(normalizer.normalize(file));
  }

  public final boolean checkWrite(String file) {
    return writeMatcher.includes(normalizer.normalize(file));
  }

  public final boolean checkExecute(String exec) {
    if (execIncludes(exec)) {
      return true;
    }
    return false;
  }

  public void store(final OutputStream out) throws IOException {
    final BufferedWriter writer =
        new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

    @SuppressWarnings("serial")
    class WrappedIOException extends RuntimeException {
      public WrappedIOException(IOException cause) {
        super(cause);
      }
    }

    class Visitor implements PathMatcher.RuleVisitor {
      private final String prefix;

      public Visitor(String prefix) {
        this.prefix = prefix;
      }

      @Override
      public void visit(boolean includes, String path) {
        try {
          writer.write(includes ? "+" : "-");
          writer.write(prefix);
          writer.write(path);
          writer.newLine();
        } catch (IOException e) {
          throw new WrappedIOException(e);
        }
      }
    }

    try {
      try {
        writer.write(normalizer.getBasedir());
        writer.newLine();
        writer.flush();
      } catch (IOException e) {
        throw new WrappedIOException(e);
      }
      readMatcher.traverse(new Visitor("R "));
      writeMatcher.traverse(new Visitor("W "));
      Visitor execVisitor = new Visitor("E ");
      execIncludes.stream().forEach(s -> execVisitor.visit(true, s));
      writer.flush();
    } catch (WrappedIOException e) {
      throw (IOException) e.getCause();
    }
  }

  public static ProjectContext load(InputStream is) throws IOException {
    Set<String> execIncludes = new HashSet<>();
    BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    String str;
    String workspaceDirectory = r.readLine();
    final PathNormalizer normalizer = PathNormalizer.create(Paths.get(workspaceDirectory));
    Builder readMatcherBuilder = PathMatcher.builder(normalizer);
    Builder writeMatcherBuilder = PathMatcher.builder(normalizer);
    while ((str = r.readLine()) != null) {
      PathMatcher.Builder builder;
      boolean include = str.startsWith("+");
      if (str.startsWith("+R ") || str.startsWith("-R ")) {
        builder = readMatcherBuilder;
      } else if (str.startsWith("+W ") || str.startsWith("-W ")) {
        builder = writeMatcherBuilder;
      } else if (str.startsWith("+E ")) {
        builder = null;
        execIncludes.add(str.substring(3));
      } else {
        throw new IllegalArgumentException("Invalid path matcher pattern: " + str);
      }
      if (builder != null) {
        str = str.substring(3);
        if (str.endsWith("/")) {
          if (include) {
            builder.includePrefix(str);
          } else {
            builder.excludePrefix(str);
          }
        } else {
          if (include) {
            builder.includePath(str);
          } else {
            builder.excludePath(str);
          }
        }
      }
    }

    final PathMatcher readMatcher = readMatcherBuilder.build();
    final PathMatcher writeMatcher = writeMatcherBuilder.build();

    return new ProjectContext(normalizer, "<unknown>", readMatcher, writeMatcher, execIncludes);
  }

  boolean execIncludes(String execPath) {
    String exec =
        execPath.contains("/") ? execPath.substring(execPath.lastIndexOf('/') + 1) : execPath;
    return execIncludes.stream().anyMatch(pattern -> pattern.equals(exec));
  }

  @Override
  public String toString() {
    return id;
  }

  public String getId() {
    return id;
  }

  public synchronized boolean addViolation(EnforcerViolation violation) {
    return violations.add(violation);
  }

  public Set<EnforcerViolation> getViolations() {
    return new TreeSet<>(violations);
  }

  public String matchingRule(char type, String file) {
    switch (type) {
      case 'E':
        return execIncludes(file) ? file : "";
      case 'W':
        return writeMatcher.getMatchingRule(file);
      case 'R':
        return readMatcher.getMatchingRule(file);
      default:
        throw new RuntimeException("Don't know about rule type " + type);
    }
  }

}
