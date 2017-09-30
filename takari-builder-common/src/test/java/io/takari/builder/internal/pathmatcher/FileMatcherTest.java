package io.takari.builder.internal.pathmatcher;

import static com.google.common.collect.ImmutableList.of;
import static io.takari.builder.internal.pathmatcher.PathNormalizer.normalize0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.internal.pathmatcher.FileMatcher.SinglePathMatcher;

public class FileMatcherTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testSingleDirectoryMatch() throws Exception {
    File basedir = temp.newFolder();
    createDirectories(basedir, "sub");
    createFiles(basedir, "1.txt", "sub/2.txt", "sub/3.txt");

    List<String> includes = new ArrayList<>();
    includes.add("*.txt");
    FileMatcher matcher = FileMatcher.absoluteMatcher(basedir.toPath(), includes, null);
    assertFiles(getMatchingFiles(matcher, basedir), new File(basedir, "1.txt"));
  }

  @Test
  public void testNestedDirectoryMatch() throws Exception {
    File basedir = temp.newFolder();
    createDirectories(basedir, "sub", "sub2", "sub/sub");
    createFiles(basedir, "1.txt", "sub/2.txt", "sub2/3.txt", "sub/sub/4.txt", "sub/sub/5.txt");

    List<String> includes = new ArrayList<>();
    includes.add("**/*.txt");
    FileMatcher matcher = FileMatcher.absoluteMatcher(basedir.toPath(), includes, null);
    assertFiles(getMatchingFiles(matcher, basedir), new File(basedir, "1.txt"),
        new File(basedir, "sub/2.txt"), new File(basedir, "sub2/3.txt"),
        new File(basedir, "sub/sub/4.txt"), new File(basedir, "sub/sub/5.txt"));
  }

  @Test
  public void testSubdirMatchers() throws Exception {
    File basedir = temp.newFolder();
    createDirectories(basedir, "sub", "sub2", "sub/sub");
    createFiles(basedir, "1.txt", "sub/2.txt", "sub2/3.txt", "sub/sub/4.txt", "sub/sub/5.txt");

    Map<Path, FileMatcher> matchers =
        FileMatcher.subMatchers(basedir.toPath(), of("sub2/*.txt", "sub/sub/*.txt"), null);

    assertThat(matchers.keySet()) //
        .hasSize(2) //
        .containsOnly(new File(basedir, "sub2").toPath(), new File(basedir, "sub/sub").toPath());

    assertFiles(getMatchingFiles(matchers), new File(basedir, "sub2/3.txt"),
        new File(basedir, "sub/sub/4.txt"), new File(basedir, "sub/sub/5.txt"));
  }

  @Test
  public void testSubdirMatchers_excludesFile() throws Exception {
    File basedir = temp.newFolder();
    createDirectories(basedir, "sub", "sub2", "sub/sub");
    createFiles(basedir, "1.txt", "sub/2.txt", "sub2/3.txt", "sub/sub/4.txt", "sub/sub/5.txt");

    Map<Path, FileMatcher> matchers = FileMatcher.subMatchers(basedir.toPath(),
        of("sub2/**/*.txt", "sub/sub/**/*.txt"), of("**/3.txt"));

    assertThat(matchers.keySet()) //
        .containsOnly(new File(basedir, "sub2").toPath(), new File(basedir, "sub/sub").toPath());

    assertFiles(getMatchingFiles(matchers), new File(basedir, "sub/sub/4.txt"),
        new File(basedir, "sub/sub/5.txt"));
  }

  @Test
  public void testSubdirMatchers_excludesDir() throws Exception {
    File basedir = temp.newFolder();
    createDirectories(basedir, "sub", "sub2", "sub/sub");
    createFiles(basedir, "1.txt", "sub/2.txt", "sub2/3.txt", "sub/sub/4.txt", "sub/sub/5.txt");

    Map<Path, FileMatcher> matchers =
        FileMatcher.subMatchers(basedir.toPath(), of("sub/**/*.txt"), of("sub/sub/**"));

    assertThat(matchers.keySet()) //
        .containsOnly(new File(basedir, "sub").toPath());

    assertFiles(getMatchingFiles(matchers), new File(basedir, "sub/2.txt"));
  }

  @Test
  public void testSubdirMatchers_specificFile() throws Exception {
    File basedir = temp.newFolder();
    createDirectories(basedir, "dir");
    createFiles(basedir, "dir/1.txt");
    File file = new File(basedir, "dir/1.txt");

    Map<Path, FileMatcher> matchers =
        FileMatcher.subMatchers(basedir.toPath(), of("dir/1.txt"), null);

    assertEquals(1, matchers.size());
    assertEquals(file.toPath(), matchers.keySet().iterator().next());

    FileMatcher matcher = matchers.values().iterator().next();
    assertNull(matcher.excludesMatcher);
    assertTrue(matcher.includesMatcher instanceof SinglePathMatcher);
    assertEquals(normalize0(file.toPath()), ((SinglePathMatcher) matcher.includesMatcher).path);

    assertFiles(getMatchingFiles(matchers), file);
  }

  @Test
  public void testSubdirMatchers_redundant() throws Exception {
    File basedir = temp.newFolder();
    createDirectories(basedir, "dir");
    createFiles(basedir, "dir/1.txt");
    File file = new File(basedir, "dir/1.txt");

    Map<Path, FileMatcher> matchers =
        FileMatcher.subMatchers(basedir.toPath(), of("dir/1.txt", "dir/1.txt"), null);

    assertEquals(1, matchers.size());
    assertEquals(file.toPath(), matchers.keySet().iterator().next());
    assertTrue(matchers.values().iterator().next().includesMatcher instanceof SinglePathMatcher);
    assertFiles(getMatchingFiles(matchers), file);
  }

  @Test
  public void testSubdirMatchers_shorthandAll() throws Exception {
    File basedir = temp.newFolder();
    createDirectories(basedir, "dir");
    createFiles(basedir, "dir/1.txt");
    File file = new File(basedir, "dir/1.txt");

    Map<Path, FileMatcher> matchers = FileMatcher.subMatchers(basedir.toPath(), of("dir/"), null);

    assertEquals(1, matchers.size());
    assertEquals(new File(basedir, "dir").toPath(), matchers.keySet().iterator().next());
    assertFiles(getMatchingFiles(matchers), file);
  }


  @Test
  public void testSubdirMatchers_matchAll() throws Exception {
    File basedir = temp.newFolder();
    createDirectories(basedir, "dir");
    createFiles(basedir, "dir/1.txt");
    Map<Path, FileMatcher> matchers = FileMatcher.subMatchers(basedir.toPath(), of("**"), null);

    assertEquals(1, matchers.size());
    assertEquals(basedir.toPath(), matchers.keySet().iterator().next());
    assertFiles(getMatchingFiles(matchers), new File(basedir, "dir"),
        new File(basedir, "dir/1.txt"));
  }

  @Test
  public void testSubdirMatchers_wildcard() throws Exception {
    File basedir = temp.newFolder();
    createDirectories(basedir, "dir");
    createFiles(basedir, "dir/1.txt");
    Map<Path, FileMatcher> matchers;

    matchers = FileMatcher.subMatchers(basedir.toPath(), of("d*r/**"), null);
    assertEquals(1, matchers.size());
    assertEquals(basedir.toPath(), matchers.keySet().iterator().next());
    assertFiles(getMatchingFiles(matchers), new File(basedir, "dir"),
        new File(basedir, "dir/1.txt"));

    matchers = FileMatcher.subMatchers(basedir.toPath(), of("d?r/**"), null);
    assertEquals(1, matchers.size());
    assertEquals(basedir.toPath(), matchers.keySet().iterator().next());
    assertFiles(getMatchingFiles(matchers), new File(basedir, "dir"),
        new File(basedir, "dir/1.txt"));
  }

  @Test
  public void testSubdirMatchers_basedirFile() throws Exception {
    File basedir = temp.newFolder();
    createFiles(basedir, "1.txt");
    File file = new File(basedir, "1.txt");

    Map<Path, FileMatcher> matchers = FileMatcher.subMatchers(basedir.toPath(), of("1.txt"), null);
    assertEquals(1, matchers.size());
    assertEquals(file.toPath(), matchers.keySet().iterator().next());
    assertFiles(getMatchingFiles(matchers), file);
  }

  @Test
  public void testSubdirMatchers_noIncludes() throws Exception {
    File basedir = temp.newFolder();
    createFiles(basedir, "1.txt");
    Path basepath = basedir.toPath();
    Map<Path, FileMatcher> matchers;

    matchers = FileMatcher.subMatchers(basepath, null, null);
    assertEquals(1, matchers.size());
    assertEquals(basepath, matchers.keySet().iterator().next());
    assertFiles(getMatchingFiles(matchers), new File(basedir, "1.txt"));

    matchers = FileMatcher.subMatchers(basepath, of(), null);
    assertEquals(1, matchers.size());
    assertEquals(basepath, matchers.keySet().iterator().next());
    assertFiles(getMatchingFiles(matchers), new File(basedir, "1.txt"));
  }

  private void createDirectories(File basedir, String... subdirs) {
    for (String subdir : subdirs) {
      new File(basedir, subdir).mkdir();
    }
  }

  private void createFiles(File basedir, String... files) throws Exception {
    for (String file : files) {
      new File(basedir, file).createNewFile();
    }
  }

  private List<File> getMatchingFiles(FileMatcher matcher, File basedir) throws Exception {
    return getMatchingFiles(Collections.singletonMap(basedir.toPath(), matcher));
  }

  private List<File> getMatchingFiles(Map<Path, FileMatcher> matchers) throws Exception {
    List<File> files = new ArrayList<>();
    matchers.forEach((subdir, matcher) -> {
      try (Stream<Path> paths = Files.walk(subdir)) {
        paths.filter(path -> matcher.matches(path)).forEach(path -> files.add(path.toFile()));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
    return files;
  }

  public static void assertFiles(Collection<File> actualFiles, File... expectedFiles) {
    String expected = toString(Arrays.asList(expectedFiles));
    String actual = toString(actualFiles);

    assertEquals(expected, actual);
  }

  private static String toString(Collection<File> files) {
    return files.stream().sorted() //
        .map(file -> file.getAbsolutePath()) //
        .collect(Collectors.joining("\n"));
  }

}
