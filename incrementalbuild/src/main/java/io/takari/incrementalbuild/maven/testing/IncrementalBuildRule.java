package io.takari.incrementalbuild.maven.testing;

import io.takari.incrementalbuild.maven.internal.MavenBuildContextFinalizer;
import io.takari.incrementalbuild.maven.internal.ProjectWorkspace;
import io.takari.incrementalbuild.spi.BuildContextFinalizer;
import io.takari.maven.testing.TestMavenRuntime;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Singleton;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.junit.Assert;

import com.google.inject.AbstractModule;

public class IncrementalBuildRule extends TestMavenRuntime {

  public IncrementalBuildRule() {
    super(new AbstractModule() {
      @Override
      protected void configure() {
        // log
        bind(IncrementalBuildLog.class).in(Singleton.class);

        // workspace
        bind(TestProjectWorkspace.class).in(MojoExecutionScoped.class);
        bind(ProjectWorkspace.class).to(TestProjectWorkspace.class).in(MojoExecutionScoped.class);

        // context finalizer
        bind(TestBuildContextFinalizer.class).in(MojoExecutionScoped.class);
        bind(BuildContextFinalizer.class).to(TestBuildContextFinalizer.class)
            .in(MojoExecutionScoped.class);
        bind(MavenBuildContextFinalizer.class).to(TestBuildContextFinalizer.class)
            .in(MojoExecutionScoped.class);

      }
    });
  }

  @Override
  public void executeMojo(MavenSession session, MavenProject project, MojoExecution execution)
      throws Exception {
    getBuildContextLog().clear();
    super.executeMojo(session, project, execution);
  }

  public IncrementalBuildLog getBuildContextLog() throws Exception {
    return lookup(IncrementalBuildLog.class);
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
    actual.removeAll(getBuildContextLog().getRegisteredOutputs()); // ignore recreated
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
