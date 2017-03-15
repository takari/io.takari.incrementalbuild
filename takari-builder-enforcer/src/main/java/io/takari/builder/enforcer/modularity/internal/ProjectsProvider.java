package io.takari.builder.enforcer.modularity.internal;

import java.util.Collection;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

interface ProjectsProvider {

  public MavenProject getTopLevelProject(MavenSession session);

  public Collection<MavenProject> getAllProjects(MavenSession session);

  public Collection<MavenProject> getUpstreamProjects(MavenSession session, MavenProject project,
      boolean transitive);
}
