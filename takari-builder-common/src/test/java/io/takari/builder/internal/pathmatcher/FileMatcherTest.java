package io.takari.builder.internal.pathmatcher;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.internal.pathmatcher.FileMatcher;

public class FileMatcherTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testSingleDirectoryMatch() throws Exception {
    File basedir = temp.newFolder();
    subDirectories(basedir, "sub");
    createFiles(basedir, "1.txt", "sub/2.txt", "sub/3.txt");

    List<String> includes = new ArrayList<>();
    includes.add("*.txt");
    FileMatcher matcher = FileMatcher.absoluteMatcher(basedir.toPath(), includes, null);
    assertFiles(getMatchingFiles(matcher, basedir), new File(basedir, "1.txt"));
  }

  @Test
  public void testNestedDirectoryMatch() throws Exception {
    File basedir = temp.newFolder();
    subDirectories(basedir, "sub", "sub2", "sub/sub");
    createFiles(basedir, "1.txt", "sub/2.txt", "sub2/3.txt", "sub/sub/4.txt", "sub/sub/5.txt");

    List<String> includes = new ArrayList<>();
    includes.add("**/*.txt");
    FileMatcher matcher = FileMatcher.absoluteMatcher(basedir.toPath(), includes, null);
    assertFiles(getMatchingFiles(matcher, basedir), new File(basedir, "1.txt"),
        new File(basedir, "sub/2.txt"), new File(basedir, "sub2/3.txt"),
        new File(basedir, "sub/sub/4.txt"), new File(basedir, "sub/sub/5.txt"));
  }

  private void subDirectories(File basedir, String... subdirs) {
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
    List<File> files = new ArrayList<>();
    Stream<Path> paths = Files.walk(basedir.toPath());
    paths.filter(path -> matcher.matches(path)).forEach(path -> files.add(path.toFile()));
    paths.close();
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
