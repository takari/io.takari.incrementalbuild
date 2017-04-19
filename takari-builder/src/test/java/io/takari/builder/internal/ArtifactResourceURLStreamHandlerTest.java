package io.takari.builder.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.internal.TestInputBuilder.TestArtifactMetadata;
import io.takari.builder.internal.utils.JarBuilder;

public class ArtifactResourceURLStreamHandlerTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  File newDirArtifact(String... entries) throws IOException {
    File dir = temp.newFolder();
    for (String entry : entries) {
      Files.createFile(dir.toPath().resolve(entry));
    }
    return dir;
  }

  File newJarArtifact(String... entries) throws IOException {
    return JarBuilder.create(temp.newFile()).withEntries(entries).build();
  }

  @Test
  public void testEquals() throws Exception {
    String relpath = "file.txt";

    IArtifactMetadata artifactA = new TestArtifactMetadata("g:a:1");
    File dirA = newDirArtifact(relpath);
    URL resourceA = new File(dirA, relpath).toURI().toURL();

    IArtifactMetadata artifactB = new TestArtifactMetadata("g:b:1");
    File dirB = newDirArtifact(relpath);
    URL resourceB = new File(dirB, relpath).toURI().toURL();

    assertThat(dirA).isNotEqualTo(dirB); // sanity check

    URL urlA = ArtifactResourceURLStreamHandler.newURL(artifactA, relpath, resourceA);
    URL urlB = ArtifactResourceURLStreamHandler.newURL(artifactB, relpath, resourceB);
    assertThat(urlA.equals(urlB)).isFalse();
  }
}
