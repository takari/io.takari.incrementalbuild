package io.takari.builder.internal;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

public class DirAssert {
  
  public static void assertFiles(Collection<File> actualFiles, File... expectedFiles) {
    String expected = toString(Arrays.asList(expectedFiles));
    String actual = toString(actualFiles);

    assertEquals(expected, actual);
  }

  private static String toString(Collection<File> files) {
    return files.stream().sorted() //
        .map(file -> file.toPath().normalize().toFile()) //
        .map(file -> file.getAbsolutePath()) //
        .collect(Collectors.joining("\n"));
  }

}
