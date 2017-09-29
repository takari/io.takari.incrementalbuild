package io.takari.builder.testing;

import static io.takari.builder.internal.pathmatcher.PathNormalizer.normalize0;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Collection;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.takari.builder.internal.BuilderContext;
import io.takari.builder.internal.ResourceRoot;

public class BuilderExecutionResult {

  private final BuilderExecution execution;
  private final BuilderContext context;

  BuilderExecutionResult(BuilderExecution execution, BuilderContext context) {
    this.execution = execution;
    this.context = context; // NB: is null after incremental no-change build
  }

  public BuilderExecutionResult assertNoErrors() {
    // TODO

    return this;
  }

  public BuilderExecutionResult assertOutputFiles(File basedir, String... paths) {
    // TODO only compare relative paths to make it easier to see the failures
    TreeSet<String> actual;
    if (context != null) {
      actual = context.getWrittenFiles().stream() //
          .map(s -> normalize0(s)) //
          .filter(s -> new File(s).isFile()) //
          .collect(Collectors.toCollection(TreeSet::new));
    } else {
      actual = new TreeSet<>();
    }
    TreeSet<String> expected = Stream.of(paths) //
        .map(s -> normalize0(new File(basedir, s).toPath())) //
        .collect(Collectors.toCollection(TreeSet::new));
    assertEquals("(re)created outputs", toString(expected), toString(actual));

    return this;
  }

  private static String toString(Collection<?> objects) {
    StringBuilder sb = new StringBuilder();
    objects.forEach(o -> sb.append(o.toString()).append('\n'));
    return sb.toString();
  }

  public BuilderExecutionResult assertProjectResources(ResourceRoot... resourceRoots) {
    TreeSet<String> actual = execution.projectResources.stream() //
        .map(r -> normalize0(r.getLocation()) + r.getIncludes() + r.getExcludes())
        .collect(Collectors.toCollection(TreeSet::new));
    TreeSet<String> expected = Stream.of(resourceRoots) //
        .map(r -> normalize0(r.getLocation()) + r.getIncludes() + r.getExcludes())
        .collect(Collectors.toCollection(TreeSet::new));
    assertEquals("generated project resources", toString(expected), toString(actual));

    return this;
  }

  public BuilderExecutionResult assertCompileSourceRoots(File... roots) {
    assertSourceRoots("compile source roots", execution.compileSourceRoots, roots);

    return this;
  }

  public BuilderExecutionResult assertTestCompileSourceRoots(File... roots) {
    assertSourceRoots("test compile source roots", execution.testCompileSourceRoots, roots);

    return this;
  }

  private void assertSourceRoots(String message, Collection<String> _actual, File... _expected) {
    TreeSet<String> actual = _actual.stream() //
        .map(s -> normalize0(s)) //
        .collect(Collectors.toCollection(TreeSet::new));
    TreeSet<String> expected = Stream.of(_expected) //
        .map(s -> normalize0(s.getAbsolutePath())) //
        .collect(Collectors.toCollection(TreeSet::new));
    assertEquals(message, toString(expected), toString(actual));
  }
}
