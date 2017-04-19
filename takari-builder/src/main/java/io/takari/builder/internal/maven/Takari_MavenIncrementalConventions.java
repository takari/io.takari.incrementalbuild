// copy&paste from io.takari.incrementalbuild.maven.internal.MavenIncrementalConventions
package io.takari.builder.internal.maven;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;

public class Takari_MavenIncrementalConventions {

  /**
   * Returns conventional location of MojoExecution incremental build input state
   */
  public static Path getExecutionStateLocation(MavenProject project, MojoExecution execution) {
    Path stateDirectory = getProjectStateLocation(project);
    return getExecutionStateLocationForDirectory(execution, stateDirectory);
  }

  /**
   * Returns a state file for a given execution and directory
   */
  private static Path getExecutionStateLocationForDirectory(MojoExecution execution,
      Path stateDirectory) {
    String builderId = getExecutionId(execution);
    return stateDirectory.resolve(builderId);
  }

  /**
   * Returns conventional MojoExecution identifier used by incremental build tools.
   */
  public static String getExecutionId(MojoExecution execution) {
    PluginDescriptor pluginDescriptor = execution.getMojoDescriptor().getPluginDescriptor();
    StringBuilder builderId = new StringBuilder();
    builderId.append(pluginDescriptor.getGroupId()).append('_')
        .append(pluginDescriptor.getArtifactId());
    builderId.append('_').append(execution.getGoal()).append('_')
        .append(execution.getExecutionId());
    return builderId.toString();
  }

  /**
   * Returns conventional location of MavenProject incremental build state
   */
  public static Path getProjectStateLocation(MavenProject project) {
    return Paths.get(project.getBuild().getDirectory(), "incremental3");
  }
}
