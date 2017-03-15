package io.takari.builder.internal;

import java.nio.file.Path;
import java.util.Map;

import io.takari.builder.IArtifactMetadata;

/**
 * Resolves @{@link DependencyResource} parameter values.
 */
public interface DependencyResolver {
  Map<IArtifactMetadata, Path> getProjectDependencies(boolean transitive);

  Map.Entry<IArtifactMetadata, Path> getProjectDependency(String groupId, String artifactId,
      String classifier);
}
