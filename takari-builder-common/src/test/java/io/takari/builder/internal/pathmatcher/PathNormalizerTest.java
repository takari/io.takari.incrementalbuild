package io.takari.builder.internal.pathmatcher;

import static io.takari.builder.internal.pathmatcher.PathNormalizer.normalize0;
import static io.takari.builder.internal.pathmatcher.PathNormalizer.getCanonicalPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PathNormalizerTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private static void assertIsNormalized(String path) {
    assertTrue(path.charAt(0) == '/');
    assertFalse(path.charAt(1) == '/'); // assert no doubles
    assertFalse(path.contains("\\"));
  }

  @Test
  public void testIsBasedirOrNestedFile() throws Exception {
    Path basedir = temp.newFolder().getCanonicalFile().toPath();
    PathNormalizer testee = PathNormalizer.createNormalizer(basedir);

    assertTrue(testee.isBasedirOrNestedFile(basedir.toString()));
    assertTrue(testee.isBasedirOrNestedFile(basedir.resolve("nested").toString()));

    assertFalse(testee.isBasedirOrNestedFile(temp.getRoot().getCanonicalPath()));

    String basepath = basedir.toString();
    assertFalse(basepath.endsWith(File.separator)); // sanity check
    assertFalse(testee.isBasedirOrNestedFile(basepath + "sibling"));
  }

  @Test
  public void testAbsolutePath0() throws IOException {
    String normalized = normalize0(temp.newFile().toPath());
    assertTrue(normalized.charAt(0) == '/');
    assertFalse(normalized.charAt(1) == '/'); // assert no doubles
  }

  @Test
  public void testPathSeparator0() throws IOException {
    assertFalse(normalize0(temp.newFile().toPath()).contains("\\"));
  }

  @Test
  public void testIdempotency0() throws IOException {
    String round1 = normalize0(temp.newFile().toPath());
    assertEquals(round1, normalize0(round1));
  }

  @Test
  public void testFileUrlPermission0() throws IOException {
    // this is funny "\C:\path\to\file" path on Windows
    String path = temp.newFile().toURI().toURL().openConnection().getPermission().getName();
    assertIsNormalized(normalize0(path));
  }

  @Test
  public void testFileUrlPath0() throws IOException {
    String path = temp.newFile().toURI().getPath();
    assertIsNormalized(normalize0(path));
  }

  @Test
  @Ignore("requires local path which the current process does not have permissions to access")
  public void testToCanonicalPath_noAccess() throws IOException {
    // TODO figure out how to setup tests for AccessDeniedException
    String inaccessible = "/Users/karina/Documents/Electronic Arts";
    Path path = getCanonicalPath(Paths.get(inaccessible));
    assertEquals(inaccessible, path.toString());
  }

  @Test
  public void testToCanonicalPath_doesNotExist() throws IOException {
    Path basedir = temp.getRoot().toPath();
    Path realBasedir = basedir.toRealPath();
    assertEquals(realBasedir.resolve("nope"), getCanonicalPath(basedir.resolve("nope")));
  }
}
