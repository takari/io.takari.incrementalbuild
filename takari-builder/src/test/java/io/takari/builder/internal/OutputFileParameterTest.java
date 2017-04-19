package io.takari.builder.internal;

import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.OutputFile;
import io.takari.builder.internal.BuilderInputs.OutputFileValue;

public class OutputFileParameterTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  static class _Data {
    @OutputFile(defaultValue = "target/file.txt")
    File outputDirectory;
  }

  @Test
  public void testOutputFile() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    File target = new File(basedir, "target");
    File file = new File(target, "file.txt");

    OutputFileValue outputFile = builder(basedir).build(_Data.class, "outputDirectory");

    assertEquals(file, outputFile.value());
  }

  //
  //
  //

  static class _PathData {
    @OutputFile(defaultValue = "target/file.txt")
    Path outputDirectory;
  }

  @Test
  public void testPathOutputFile() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    File target = new File(basedir, "target");
    Path path = Paths.get(target.getCanonicalPath(), "file.txt");

    OutputFileValue outputFile = builder(basedir).build(_PathData.class, "outputDirectory");

    assertEquals(path, outputFile.value());
  }
}
