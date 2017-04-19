package io.takari.builder.internal.maven;

import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.internal.Dependency;
import io.takari.builder.internal.DependencyResolver;

public class MavenProjectDependencyResolver implements DependencyResolver {

  final MavenProject project;
  
  public MavenProjectDependencyResolver(MavenProject project) {
    this.project = project;
  }
  
  // returns possibly empty collection of resolved dependencies
  @SuppressWarnings("deprecation")
  @Override
  public Map<IArtifactMetadata, Path> getProjectDependencies(boolean transitive) {
    Set<Artifact> artifacts;
    if(transitive) {
      artifacts = project.getArtifacts();
    } else {
      artifacts = project.getDependencyArtifacts();
    }
    Map<IArtifactMetadata, Path> result = new LinkedHashMap<>();
    artifacts.forEach(a -> {
      result.put(new ArtifactMetadataImpl(a), a.getFile().toPath());
    });
    return result;
  }
  
  private boolean matchesArtifact(Dependency dependency, Artifact a) {
    return dependency.matchesArtifact(a.getGroupId(), a.getArtifactId(), a.getVersion(),
        a.getClassifier());
  }

  private static class ArtifactMetadataImpl implements IArtifactMetadata {
    
    private final Artifact artifact;
    
    ArtifactMetadataImpl(Artifact artifact) {
      this.artifact = artifact;
    }

    @Override
    public String getGroupId() {
      return artifact.getGroupId();
    }

    @Override
    public String getArtifactId() {
      return artifact.getArtifactId();
    }

    @Override
    public String getVersion() {
      return artifact.getVersion();
    }

    @Override
    public String getType() {
      return artifact.getType();
    }

    @Override
    public String getClassifier() {
      return artifact.getClassifier();
    }
  }

  @Override
  public SimpleEntry<IArtifactMetadata, Path> getProjectDependency(String groupId,
      String artifactId, String classifier) {
    Dependency dependency = new Dependency(groupId, artifactId, classifier);
    Artifact artifact = project.getArtifacts().stream() //
        .filter(a -> matchesArtifact(dependency, a)) //
        .findFirst().get();
    return new SimpleEntry<>(new ArtifactMetadataImpl(artifact),
        artifact.getFile().toPath());
  }
}