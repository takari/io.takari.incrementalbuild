package io.takari.builder.internal.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.ResolutionScope;
import io.takari.builder.internal.cache.ScopedProjectDependencyCache.Key;

public class ScopedProjectDependencyCacheTest {

  public static class SimpleArtifactMetadata implements IArtifactMetadata {

    private final String groupId;

    private final String artifactId;

    public SimpleArtifactMetadata(String groupId, String artifactId) {
      this.groupId = groupId;
      this.artifactId = artifactId;
    }

    @Override
    public String getGroupId() {
      return groupId;
    }

    @Override
    public String getArtifactId() {
      return artifactId;
    }

    @Override
    public String getVersion() {
      return null;
    }

    @Override
    public String getType() {
      return null;
    }

    @Override
    public String getClassifier() {
      return null;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      SimpleArtifactMetadata other = (SimpleArtifactMetadata) obj;
      if (artifactId == null) {
        if (other.artifactId != null) return false;
      } else if (!artifactId.equals(other.artifactId)) return false;
      if (groupId == null) {
        if (other.groupId != null) return false;
      } else if (!groupId.equals(other.groupId)) return false;
      return true;
    }
  }

  @Test
  public void testGetDependencies() {
    Map<IArtifactMetadata, Path> artifacts = new LinkedHashMap<>();
    for (int i = 1; i < 10; i++) {
      artifacts.put(new SimpleArtifactMetadata("test" + i, "test" + i), null);
    }

    // On a no-op supplier, the output should be the same as the input
    ScopedProjectDependencyCache cache = new ScopedProjectDependencyCache();
    final Key key =
        ScopedProjectDependencyCache.key("test", "test", "1", ResolutionScope.TEST, false);
    Map<IArtifactMetadata, Path> results = cache.getDependencies(key, () -> {
      return artifacts;
    });
    assertThat(artifacts.equals(results));

    // On a repeated call, the output should match the previous output including order of the
    // entries
    Map<IArtifactMetadata, Path> repeatResults = cache.getDependencies(key, () -> {
      return artifacts;
    });
    assertThat(results.equals(repeatResults));
  }

}
