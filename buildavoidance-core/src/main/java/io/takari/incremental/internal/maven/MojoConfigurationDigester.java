package io.takari.incremental.internal.maven;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;

@Named
@MojoExecutionScoped
public class MojoConfigurationDigester {

  @Inject
  public MojoConfigurationDigester(MavenSession session, MavenProject project,
      MojoExecution execution, Logger logger) {

  }

  public Map<String, byte[]> digest() {
    // TODO Auto-generated method stub
    return null;
  }

}
