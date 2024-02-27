/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.maven;

import io.takari.builder.internal.cache.ScopedProjectDependencyCache;
import io.takari.builder.internal.resolver.ArtifactResolverProvider;
import io.takari.builder.internal.resolver.DependencyResolver;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;

@Named(MavenArtifactResolverProvider.MAVEN)
@SessionScoped
public class MavenArtifactResolverProvider implements ArtifactResolverProvider<MavenProject> {

    public static final String MAVEN = "maven";

    private final MavenSession session;
    private final RepositorySystem repositorySystem;
    private final ScopedProjectDependencyCache dependencyCache;

    @Inject
    public MavenArtifactResolverProvider(
            MavenSession session, RepositorySystem repositorySystem, ScopedProjectDependencyCache dependencyCache) {
        this.session = session;
        this.repositorySystem = repositorySystem;
        this.dependencyCache = dependencyCache;
    }

    @Override
    public DependencyResolver getResolver(MavenProject project) {
        return new MavenDependencyResolver(project, session.getRepositorySession(), repositorySystem, dependencyCache);
    }
}
