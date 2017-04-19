package io.takari.builder.enforcer.modularity;

import org.apache.maven.execution.ProjectDependencyGraph;

public interface AlternateGraphProvider {
  ProjectDependencyGraph get(ProjectDependencyGraph graph);
}