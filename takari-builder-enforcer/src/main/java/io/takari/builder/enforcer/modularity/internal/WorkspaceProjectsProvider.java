/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.enforcer.modularity.internal;

import io.takari.builder.enforcer.modularity.AlternateGraphProvider;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;

@Named
public class WorkspaceProjectsProvider implements ProjectsProvider {

    // can't use sisu-provided @Nullable because it is not exported from Maven core
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Nullable {}

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
    public Collection<MavenProject> getUpstreamProjects(
            MavenSession session, MavenProject project, boolean transitive) {
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
