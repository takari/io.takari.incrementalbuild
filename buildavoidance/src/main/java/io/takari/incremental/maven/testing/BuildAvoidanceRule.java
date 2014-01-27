package io.takari.incremental.maven.testing;

import java.io.File;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.MojoExecutionListener;
import org.apache.maven.execution.scope.internal.MojoExecutionScope;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;

public class BuildAvoidanceRule extends MojoRule {

  public BuildAvoidanceRule() {
    super(new AbstractBuildAvoidanceTest());
  }

  public void executeMojo(MavenSession session, MavenProject project, MojoExecution execution)
      throws Exception {
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

  public BuildContextLog getBuildContext() throws Exception {
    return lookup(BuildContextLog.class);
  }
}
