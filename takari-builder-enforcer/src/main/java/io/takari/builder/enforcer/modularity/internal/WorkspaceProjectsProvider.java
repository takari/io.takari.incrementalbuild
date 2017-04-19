package io.takari.builder.enforcer.modularity.internal;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;

import io.takari.builder.enforcer.modularity.AlternateGraphProvider;


@Named
public class WorkspaceProjectsProvider implements ProjectsProvider {
  
  //can't use sisu-provided @Nullable because it is not exported from Maven core
  @Retention(RetentionPolicy.RUNTIME)
  public static @interface Nullable {
  }
  
  private final Optional<AlternateGraphProvider> graphFilter;
  
  @Inject
  WorkspaceProjectsProvider(@Nullable AlternateGraphProvider graphFilter) {
    this.graphFilter = Optional.ofNullable(graphFilter);
  }

  @Override
  public MavenProject getTopLevelProject(MavenSession session) {
      return session.getTopLevelProject();
  }

  @Override
  public Collection<MavenProject> getAllProjects(MavenSession session) {
	  ProjectDependencyGraph graph = getGenerationsGraph(session);
      return graph.getSortedProjects();
  }

  @Override
  public Collection<MavenProject> getUpstreamProjects(MavenSession session, MavenProject project, boolean transitive) {
	  ProjectDependencyGraph graph = getGenerationsGraph(session);
      return graph.getUpstreamProjects(project, transitive);
  }


  private ProjectDependencyGraph getGenerationsGraph(MavenSession session) {
    ProjectDependencyGraph graph = session.getProjectDependencyGraph();
    if (graphFilter.isPresent()) {
      return graphFilter.get().get(graph);
    }
    return graph;
  }
}
