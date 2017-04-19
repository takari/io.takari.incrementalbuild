package io.takari.builder.demo;

import static io.takari.builder.testing.BuilderExecution.builderExecution;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.testing.BuilderExecution;
import io.takari.builder.testing.BuilderExecutionException;
import io.takari.builder.testing.BuilderExecutionResult;

/**
 * {@link BuilderExecution} API is the recommended way to test Builders. BuilderExecution provides
 * control over all builder input parameters while {@link BuilderExecutionResult} gives access to
 * all builder outputs.
 */
public class CopyFilesBuilderTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testCopyFiles() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    File target = temp.newFolder().getCanonicalFile();

    new File(basedir, "file.txt").createNewFile();
    new File(basedir, "subdir/subsubdir").mkdirs();
    new File(basedir, "subdir/subsubdir/file.txt").createNewFile();

    builderExecution(basedir, CopyFilesBuilder.class) //
        .withInputDirectory("inputs", basedir) //
        .withConfiguration("to", target.getPath()) //
        .execute() //
        .assertOutputFiles(target, "file.txt", "subdir/subsubdir/file.txt");
  }

  @Test
  public void testCopyFiles_invalidInput() throws Exception {
    thrown.expect(BuilderExecutionException.class);
    thrown.expectMessage("invalid file length");

    File basedir = temp.newFolder().getCanonicalFile();
    File target = temp.newFolder().getCanonicalFile();

    Path file = new File(basedir, "file.bin").toPath();
    try (OutputStream w = Files.newOutputStream(file)) {
      for (int i = 0; i < 13; i++) {
        w.write(i);
      }
    }

    builderExecution(basedir, CopyFilesBuilder.class) //
        .withConfigurationXml("inputs", "<location>" + basedir + "</location>") //
        .withConfiguration("to", target.getPath()) //
        .execute();
  }
}
