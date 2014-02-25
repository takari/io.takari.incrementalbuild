package io.takari.incremental.demo;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultInput;
import io.takari.incrementalbuild.spi.DefaultInputMetadata;
import io.takari.incrementalbuild.spi.ResourceHolder;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import org.apache.maven.artifact.Artifact;

/**
 * Maven plugin uses one file from dependency artifact.
 */
public class DependencyResourceMockup {

  DefaultBuildContext<?> context;

  Artifact artifact;
  String artifactPath;

  static class ArtifactFile implements Serializable {
    final String groupId;
    final String artifactId;
    final String path;

    public ArtifactFile(Artifact artifact, String path) {
      this.groupId = artifact.getGroupId();
      this.artifactId = artifact.getArtifactId();
      this.path = path;
    }

    @Override
    public int hashCode() {
      int hash = 31;
      hash = hash * 17 + groupId.hashCode();
      hash = hash * 17 + artifactId.hashCode();
      hash = hash * 17 + path.hashCode();
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }

      if (!(obj instanceof ArtifactFile)) {
        return false;
      }

      ArtifactFile other = (ArtifactFile) obj;

      return groupId.equals(other.groupId) && artifactId.equals(other.artifactId)
          && path.equals(other.path);
    }
  }

  static class ArtifactFileHolder implements ResourceHolder<ArtifactFile> {

    final ArtifactFile artifactFile;

    public ArtifactFileHolder(Artifact artifact, String path) {
      this.artifactFile = new ArtifactFile(artifact, path);
    }

    @Override
    public ArtifactFile getResource() {
      return artifactFile;
    }

    @Override
    public ResourceStatus getStatus() {
      // status of this resource can't be determined without looking at new resolution results
      // this is not supported directly by BuildContext, so this will be handled in client code
      return ResourceStatus.UNMODIFIED;
    }
  }

  public void execute() throws IOException {
    File file = artifact.getFile();

    // ArtifactFile is (groupId, artifactId, path) 3-tuple
    DefaultInputMetadata<ArtifactFile> metadata =
        context.registerInput(new ArtifactFileHolder(artifact, artifactPath));

    File oldFile = metadata.getValue("file", File.class);
    long oldLength = metadata.getValue("file.length", Long.class);
    long oldLastModified = metadata.getValue("file.lastModified", Long.class);

    if (file.equals(oldFile) && file.lastModified() == oldLastModified
        && file.length() == oldLength) {
      // artifact/path is up-to-date, no need to process it again

      return;
    }

    // artifact has changed, check if resource itself changed or not

    // all interesting state is held in metadata attributes

    // does artifact.file have the same path as before?
    // if artifact.file is a file
    // - does artifact.file have the same lastModified and length as before?
    // - does archive /path entry inside artifact.file have the same sha1 as before?
    // if artifact.file is a directory
    // - does directory /path file have the same sha1 as before?

    if (metadata.getStatus() != ResourceStatus.UNMODIFIED) {
      doSomethingUsefulWithResource(metadata.process());
    }
  }

  private void doSomethingUsefulWithResource(DefaultInput<ArtifactFile> process) {


  }

}
