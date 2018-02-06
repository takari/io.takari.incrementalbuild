package io.takari.builder.internal.maven;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;

import io.takari.builder.internal.cache.ScopedProjectDependencyCache;
import io.takari.builder.internal.resolver.ArtifactResolverProvider;
import io.takari.builder.internal.resolver.DependencyResolver;

@Named(MavenArtifactResolverProvider.MAVEN)
@SessionScoped
public class MavenArtifactResolverProvider implements ArtifactResolverProvider<MavenProject> {

  public final static String MAVEN = "maven";

  private final MavenSession session;
  private final RepositorySystem repositorySystem;
  private final ScopedProjectDependencyCache dependencyCache;

  @Inject
  public MavenArtifactResolverProvider(MavenSession session, RepositorySystem repositorySystem,
      ScopedProjectDependencyCache dependencyCache) {
    this.session = session;
    this.repositorySystem = repositorySystem;
    this.dependencyCache = dependencyCache;
  }

  @Override
  public DependencyResolver getResolver(MavenProject project) {
    return new MavenDependencyResolver(project, session.getRepositorySession(), repositorySystem,
        dependencyCache);
  }

}
