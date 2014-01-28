package io.takari.incrementalbuild.internal.maven;

import java.io.File;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;

/**
 * @author igor
 */
@Named
@Singleton
public class MavenIncrementalConventions {

  /**
   * Returns conventional location of MojoExecution incremental build state
   */
  public File getExecuteStateLocation(MavenProject project, MojoExecution execution) {
    File stateDirectory = getProjectStateLocation(project);
    String builderId = getExecutionId(execution);
    return new File(stateDirectory, builderId);
  }

  /**
   * Returns conventional MojoExecution identifier used by incremental build tools.
   */
  public String getExecutionId(MojoExecution execution) {
    PluginDescriptor pluginDescriptor = execution.getMojoDescriptor().getPluginDescriptor();
    StringBuilder builderId = new StringBuilder();
    builderId.append(pluginDescriptor.getGroupId()).append(':')
        .append(pluginDescriptor.getArtifactId());
    builderId.append(':').append(execution.getGoal()).append(':')
        .append(execution.getExecutionId());
    return builderId.toString();
  }

  /**
   * Returns conventional location of MavenProject incremental build state
   */
  public File getProjectStateLocation(MavenProject project) {
    return new File(project.getBuild().getDirectory(), "incremental");
  }
}
