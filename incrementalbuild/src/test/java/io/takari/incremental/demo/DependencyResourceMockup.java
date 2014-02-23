package io.takari.incremental.demo;

import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.ResourceHolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;

/**
 * Maven plugin uses one file from dependency artifact.
 */
public class DependencyResourceMockup {

  private static final String RESOURCE_PATH = "some/file";

  DefaultBuildContext<?> context;

  Artifact dependency;

  private static class ArtifactResourceKey implements Serializable {
    private final String groupId;
    private final String artifactId;
    private final String path;

    public ArtifactResourceKey(String groupId, String artifactId, String path) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.path = path;
    }

    @Override
    public int hashCode() {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      // TODO Auto-generated method stub
      return false;
    }
  }

  private static class ArtifactResource implements ResourceHolder<ArtifactResourceKey> {

    private ArtifactResource(ArtifactResourceKey key, byte[] digest) {
      // TODO Auto-generated method stub
    }

    @Override
    public ArtifactResourceKey getResource() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public ResourceStatus getStatus() {
      // TODO Auto-generated method stub
      return null;
    }

    public static ArtifactResource fromFile(Artifact artifact, String path, File resource) {
      // TODO Auto-generated method stub
      return null;
    }

    public static ArtifactResource fromJarEntry(Artifact artifact, String path, JarFile jar,
        JarEntry entry) {
      // TODO Auto-generated method stub
      return null;
    }

    public static ArtifactResource fromNothing(Artifact artifact, String path) {
      // TODO Auto-generated method stub
      return null;
    }
  }

  public void execute() throws IOException {
    File file = dependency.getFile();

    if (file == null || !file.exists()) {
      // artifact was not resolved
      throw new IllegalStateException();
    }

    if (file.isFile()) {
      InputMetadata<File> jarMetadata = context.registerInput(file);
      if (jarMetadata.getStatus() == ResourceStatus.UNMODIFIED) {
        // jar has not changed since last build
        // need to register the resource to indicate it is still relevant
        // otherwise resource's outputs will be considered orphaned are removed
        context.registerInput(ArtifactResource.fromNothing(dependency, RESOURCE_PATH));
      } else {
        // jar changed, need to look inside and check if jar entry changed or not
        JarFile jar = new JarFile(file);
        try {
          JarEntry resource = jar.getJarEntry(RESOURCE_PATH);
          InputMetadata<ArtifactResourceKey> metadata =
              context.registerInput(ArtifactResource.fromJarEntry(dependency, RESOURCE_PATH, jar,
                  resource));
          if (metadata.getStatus() != ResourceStatus.UNMODIFIED) {
            InputStream is = jar.getInputStream(resource);
            try {
              doSomethingUsefulWithResource(metadata.process(), is);
            } finally {
              is.close();
            }
          }
        } finally {
          jar.close();
        }
      }
    } else if (file.isDirectory()) {
      File resource = new File(file, RESOURCE_PATH);
      InputMetadata<ArtifactResourceKey> metadata =
          context.registerInput(ArtifactResource.fromFile(dependency, RESOURCE_PATH, resource));
      if (metadata.getStatus() != ResourceStatus.UNMODIFIED) {
        InputStream is = new FileInputStream(resource);
        try {
          doSomethingUsefulWithResource(metadata.process(), is);
        } finally {
          is.close();
        }
      }
    }
  }

  private void doSomethingUsefulWithResource(Input<ArtifactResourceKey> input, InputStream is) {}
}
