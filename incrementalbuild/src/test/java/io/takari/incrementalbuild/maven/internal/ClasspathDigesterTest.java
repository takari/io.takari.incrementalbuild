package io.takari.incrementalbuild.maven.internal;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.Assert;
import org.junit.Test;

public class ClasspathDigesterTest {

  @Test
  public void testEntryCache() throws Exception {
    ClasspathDigester digester = new ClasspathDigester(new ConcurrentHashMap<String, byte[]>());
    ArtifactHandler handler = new DefaultArtifactHandler("jar");
    Artifact a = new DefaultArtifact("g", "a", "1", Artifact.SCOPE_COMPILE, "jar", null, handler);
    a.setFile(new File("src/test/projects/digester"));
    Serializable cached = digester.digest(Collections.singletonList(a));
    Serializable digest = digester.digest(Collections.singletonList(a));
    Assert.assertTrue(digest.equals(cached));
  }
}
