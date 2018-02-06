package io.takari.builder.internal.maven;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.filter.AndDependencyFilter;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.ResolutionScope;
import io.takari.builder.internal.Dependency;
import io.takari.builder.internal.cache.ScopedProjectDependencyCache;
import io.takari.builder.internal.cache.ScopedProjectDependencyCache.Key;
import io.takari.builder.internal.resolver.DependencyResolver;

public class MavenDependencyResolver implements DependencyResolver {

  private static class TransitiveDependencyFilter implements DependencyFilter {

    private Set<String> keys = new HashSet<>();

    public TransitiveDependencyFilter(Collection<org.apache.maven.model.Dependency> dependencies) {
      for (org.apache.maven.model.Dependency dependency : dependencies) {
        String key = ArtifactUtils.key(dependency.getGroupId(), dependency.getArtifactId(),
            dependency.getVersion());
        keys.add(key);
      }
    }

    @Override
    public boolean accept(DependencyNode node, List<DependencyNode> parents) {
      org.eclipse.aether.graph.Dependency dependency = node.getDependency();
      if (dependency != null) {
        org.eclipse.aether.artifact.Artifact a = dependency.getArtifact();
        String key = ArtifactUtils.key(a.getGroupId(), a.getArtifactId(), a.getVersion());
        return keys.contains(key);
      }
      return false;
    }

  }

  private final MavenProject project;
  private final RepositorySystemSession repoSession;
  private final RepositorySystem repositorySystem;
  private final ScopedProjectDependencyCache dependencyCache;

  public MavenDependencyResolver(MavenProject project, RepositorySystemSession repoSession,
      RepositorySystem repositorySystem, ScopedProjectDependencyCache dependencyCache) {
    this.project = project;
    this.repoSession = repoSession;
    this.repositorySystem = repositorySystem;
    this.dependencyCache = dependencyCache;
  }

  @Override
  public Map.Entry<IArtifactMetadata, Path> getProjectDependency(String groupId, String artifactId,
      String classifier, ResolutionScope scope) {
    Dependency dependency = new Dependency(groupId, artifactId, classifier);

    return resolveDependencies(scope, true).entrySet().stream() //
        .filter(a -> matchesArtifact(dependency, a.getKey())) //
        .findFirst().get();
  }

  // returns possibly empty collection of resolved dependencies
  @Override
  public Map<IArtifactMetadata, Path> getProjectDependencies(boolean transitive,
      ResolutionScope scope) {

    return resolveDependencies(scope, transitive);
  }

  private boolean matchesArtifact(Dependency dependency, IArtifactMetadata a) {
    return dependency.matchesArtifact(a.getGroupId(), a.getArtifactId(), a.getVersion(),
        a.getClassifier());
  }

  private Map<IArtifactMetadata, Path> resolveDependencies(ResolutionScope scope,
      boolean transitive) {
    final Key key = ScopedProjectDependencyCache.key(project.getGroupId(), project.getArtifactId(),
        project.getVersion(), scope, transitive);

    return dependencyCache.getDependencies(key, () -> {
      Set<Artifact> artifacts = resolveArtifacts(scope, transitive);
      Map<IArtifactMetadata, Path> results = new LinkedHashMap<>();

      artifacts.forEach(a -> {
        results.put(new MavenArtifactMetadata(a), a.getFile().toPath());
      });

      return results;
    });
  }

  private Set<Artifact> resolveArtifacts(ResolutionScope scope, boolean transitive) {

    DependencyResolutionResult result;
    DependencyFilter resolutionFilter = getResolutionFilter(scope, transitive);

    try {
      result = resolveDependencies(scope, resolutionFilter);
    } catch (DependencyResolutionException e) {
      result = e.getResult();
    }

    Set<Artifact> artifacts = new LinkedHashSet<>();
    if (result.getDependencyGraph() != null
        && !result.getDependencyGraph().getChildren().isEmpty()) {
      RepositoryUtils.toArtifacts(artifacts, result.getDependencyGraph().getChildren(),
          Collections.singletonList(project.getArtifact().getId()), resolutionFilter);
    }
    return artifacts;
  }

  private DependencyFilter getResolutionFilter(ResolutionScope scope, boolean transitive) {
    DependencyFilter filter = new ScopeDependencyFilter(impliedScopes(scope), null);
    if (!transitive) {
      filter = AndDependencyFilter.newInstance(filter,
          new TransitiveDependencyFilter(project.getDependencies()));
    }

    return filter;
  }

  private static Collection<String> impliedScopes(ResolutionScope scope) {
    if (ResolutionScope.COMPILE.equals(scope)) {
      return Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_SYSTEM, Artifact.SCOPE_PROVIDED);
    } else if (ResolutionScope.RUNTIME.equals(scope)) {
      return Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME);
    } else if (ResolutionScope.COMPILE_PLUS_RUNTIME.equals(scope)) {
      return Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_SYSTEM, Artifact.SCOPE_PROVIDED,
          Artifact.SCOPE_RUNTIME);
    } else if (ResolutionScope.TEST.equals(scope)) {
      return Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_SYSTEM, Artifact.SCOPE_PROVIDED,
          Artifact.SCOPE_RUNTIME, Artifact.SCOPE_TEST);
    }
    return Collections.emptyList();
  }

  private DependencyResolutionResult resolveDependencies(ResolutionScope scope,
      DependencyFilter resolutionFilter) throws DependencyResolutionException {
    ClassLoader tccl = Thread.currentThread().getContextClassLoader();
    try {
      ClassLoader projectRealm = project.getClassRealm();
      if (projectRealm != null && projectRealm != tccl) {
        Thread.currentThread().setContextClassLoader(projectRealm);
      }

      RequestTrace trace = RequestTrace.newChild(null, resolutionFilter);
      ScopeBasedDependencyResolutionResult result = new ScopeBasedDependencyResolutionResult();
      ArtifactTypeRegistry stereotypes = repoSession.getArtifactTypeRegistry();
      CollectRequest collect = new CollectRequest();
      collect.setRootArtifact(RepositoryUtils.toArtifact(project.getArtifact()));
      collect.setRequestContext("projectScope");
      collect.setRepositories(project.getRemoteProjectRepositories());

      for (org.apache.maven.model.Dependency dependency : project.getDependencies()) {
        if (StringUtils.isEmpty(dependency.getGroupId())
            || StringUtils.isEmpty(dependency.getArtifactId())
            || StringUtils.isEmpty(dependency.getVersion())) {
          // guard against case where best-effort resolution for invalid models is requested
          continue;
        }
        collect.addDependency(RepositoryUtils.toDependency(dependency, stereotypes));
      }

      DependencyManagement depMngt = project.getDependencyManagement();
      if (depMngt != null) {
        for (org.apache.maven.model.Dependency dependency : depMngt.getDependencies()) {
          collect.addManagedDependency(RepositoryUtils.toDependency(dependency, stereotypes));
        }
      }

      DependencyRequest depRequest = new DependencyRequest(collect, resolutionFilter);
      depRequest.setTrace(trace);

      collect.setTrace(RequestTrace.newChild(trace, depRequest));

      DependencyNode node;
      try {
        node = repositorySystem.collectDependencies(repoSession, collect).getRoot();
        result.setDependencyGraph(node);
      } catch (DependencyCollectionException e) {
        result.setDependencyGraph(e.getResult().getRoot());
        e.printStackTrace();
        result.setCollectionErrors(e.getResult().getExceptions());

        throw new DependencyResolutionException(result,
            "Could not resolve dependencies for project " + project.getId() + ", with scope "
                + scope + ": " + e.getMessage(),
            e);
      }

      depRequest.setRoot(node);

      try {
        process(result,
            repositorySystem.resolveDependencies(repoSession, depRequest).getArtifactResults());
      } catch (org.eclipse.aether.resolution.DependencyResolutionException e) {
        e.printStackTrace();
        process(result, e.getResult().getArtifactResults());

        throw new DependencyResolutionException(result,
            "Could not resolve dependencies for project " + project.getId() + ", with scope "
                + scope + ": " + e.getMessage(),
            e);
      }

      return result;
    } finally {
      Thread.currentThread().setContextClassLoader(tccl);
    }
  }

  private void process(ScopeBasedDependencyResolutionResult result,
      Collection<ArtifactResult> results) {
    for (ArtifactResult ar : results) {
      DependencyNode node = ar.getRequest().getDependencyNode();
      if (ar.isResolved()) {
        result.addResolvedDependency(node.getDependency());
      } else {
        result.setResolutionErrors(node.getDependency(), ar.getExceptions());
      }
    }
  }
}
