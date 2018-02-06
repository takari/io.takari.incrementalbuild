package io.takari.builder.internal.resolver;

import java.nio.file.Path;
import java.util.Map;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.ResolutionScope;

/**
 * Resolves @{@link DependencyResource} parameter values.
 */
public interface DependencyResolver {
  Map<IArtifactMetadata, Path> getProjectDependencies(boolean transitive, ResolutionScope scope);

  Map.Entry<IArtifactMetadata, Path> getProjectDependency(String groupId, String artifactId,
      String classifier, ResolutionScope scope);
}
