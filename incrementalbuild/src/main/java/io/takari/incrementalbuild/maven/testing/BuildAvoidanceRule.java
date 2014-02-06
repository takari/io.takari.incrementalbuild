package io.takari.incrementalbuild.maven.testing;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.MojoExecutionListener;
import org.apache.maven.execution.scope.internal.MojoExecutionScope;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;

public class BuildAvoidanceRule extends MojoRule {

  public BuildAvoidanceRule() {
    super(new BuildAvoidanceRuntime());
  }

  public void executeMojo(MavenSession session, MavenProject project, MojoExecution execution)
      throws Exception {
    getBuildContextLog().clear();

    MojoExecutionScope scope = lookup(MojoExecutionScope.class);
    try {
      scope.enter();

      scope.seed(MavenSession.class, session);
      scope.seed(MavenProject.class, project);
      scope.seed(MojoExecution.class, execution);

      Mojo mojo = lookupConfiguredMojo(session, execution);
      mojo.execute();

      for (MojoExecutionListener listener : getContainer().lookupList(MojoExecutionListener.class)) {
        listener
            .afterMojoExecutionSuccess(new MojoExecutionEvent(session, project, execution, mojo));
      }
    } finally {
      scope.exit();
    }
  }

  @Override
  public void executeMojo(File basedir, String goal) throws Exception {
    MavenProject project = readMavenProject(basedir);
    MojoExecution execution = newMojoExecution(goal);
    MavenSession session = newMavenSession(project);

    executeMojo(session, project, execution);
  }

  public BuildContextLog getBuildContextLog() throws Exception {
    return lookup(BuildContextLog.class);
  }

  /**
   * Asserts specified paths were output during the build
   */
  public void assertBuildOutputs(File basedir, String... paths) throws Exception {
    Set<File> expected = toFileSet(basedir, paths);
    Set<File> actual = new TreeSet<File>(getBuildContextLog().getRegisteredOutputs());
    Assert.assertEquals("(re)created outputs", toString(expected), toString(actual, true));
  }

  /**
   * Asserts specified paths were deleted during the build
   */
  public void assertDeletedOutputs(File basedir, String... paths) throws Exception {
    Set<File> expected = toFileSet(basedir, paths);
    Set<File> actual = new TreeSet<File>(getBuildContextLog().getDeletedOutputs());
    Assert.assertEquals("deleted outputs", toString(expected), toString(actual, false));
  }

  public void assertCarriedOverOutputs(File basedir, String... paths) throws Exception {
    Set<File> expected = toFileSet(basedir, paths);
    Set<File> actual = new TreeSet<File>(getBuildContextLog().getCarriedOverOutputs());
    Assert.assertEquals("carried over outputs", toString(expected), toString(actual, true));
  }

  /**
   * Asserts messages were associated with the specified path during the build.
   */
  public void assertMessages(File basedir, String path, String... messages) throws Exception {
    List<String> actual =
        new ArrayList<String>(getBuildContextLog().getMessages(new File(basedir, path)));
    List<String> expected = Arrays.asList(messages);
    Assert.assertEquals(toString(expected), toString(actual));
  }

  private static String toString(Collection<?> objects) {
    StringBuilder sb = new StringBuilder();
    for (Object file : objects) {
      sb.append(file.toString()).append('\n');
    }
    return sb.toString();
  }

  private static String toString(Collection<File> files, boolean canRead) {
    StringBuilder sb = new StringBuilder();
    for (File file : files) {
      if (canRead != file.canRead()) {
        sb.append(file.canRead() ? "EXISTS " : "DOES NOT EXIST ");
      }
      sb.append(file.toString()).append('\n');
    }
    return sb.toString();
  }


  private Set<File> toFileSet(File basedir, String... paths) {
    Set<File> expected = new TreeSet<File>();
    for (String path : paths) {
      expected.add(new File(basedir, path));
    }
    return expected;
  }
}
