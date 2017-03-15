package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderInputs.digest;
import static io.takari.builder.internal.BuilderInputsBuilder.SOURCE_ROOTS_EXPR;
import static io.takari.builder.internal.BuilderInputsBuilder.TEST_SOURCE_ROOTS_EXPR;
import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.InputFile;
import io.takari.builder.internal.BuilderInputs.CollectionValue;
import io.takari.builder.internal.BuilderInputs.Digest;
import io.takari.builder.internal.BuilderInputs.InputFileValue;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidConfigurationException;

public class FileInputTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  //
  //
  //

  @Test
  public void testDigest() throws Exception {
    Digest digest = digest(new InputFileValue(File.class, new File("a").toPath()));

    assertEquals(digest, digest(new InputFileValue(File.class, new File("a").toPath())));
    assertNotEquals(digest, digest(new InputFileValue(File.class, new File("b").toPath())));
  }

  //
  //
  //

  static class _Data {
    @InputFile(fileRequired = true)
    File file;
  }

  @Test
  public void testInputFile() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    File file = new File(basedir, "a.txt");
    file.createNewFile();

    InputFileValue fileInput = builder(basedir) //
        .withConfigurationXml("<file>a.txt</file>") //
        .build(_Data.class, "file");

    assertEquals(file, fileInput.value());
  }

  @Test
  public void testInputFile_isDirectory() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("a.txt is a directory");

    File basedir = temp.newFolder().getCanonicalFile();
    new File(basedir, "a.txt").mkdirs();

    builder(basedir) //
        .withConfigurationXml("<file>a.txt</file>") //
        .build(_Data.class, "file");
  }

  @Test
  public void testInputFileDoesNotExist() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("_Data.file: is required");

    File basedir = temp.newFolder().getCanonicalFile();

    builder(basedir) //
        .withConfigurationXml("<file>a.txt</file>") //
        .build(_Data.class, "file");
  }

  //
  //
  //

  static class _PathData {
    @InputFile(fileRequired = true)
    Path file;
  }

  @Test
  public void testPathInputFile() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    Path path = Paths.get(basedir.getCanonicalPath(), "a.txt");
    Files.createFile(path);

    InputFileValue fileInput = builder(basedir) //
        .withConfigurationXml("<file>a.txt</file>") //
        .build(_PathData.class, "file");

    assertEquals(path, fileInput.value());
  }

  //
  //
  //

  static class _OptionalData {
    @InputFile(fileRequired = false)
    File file;
  }

  @Test
  public void testOptionalFile() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    InputFileValue fileInput = builder(basedir) //
        .build(_OptionalData.class, "file");

    assertNull(fileInput);
  }

  @Test
  public void testOptionalFileDoesNotExist() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    InputFileValue fileInput = builder(basedir) //
        .withConfigurationXml("<file>a.txt</file>") //
        .build(_OptionalData.class, "file");

    assertEquals(new File(basedir, "a.txt"), fileInput.value());
  }

  @Test
  public void testOptionalFileExists() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    new File(basedir, "a.txt").createNewFile();

    File file = (File) builder(basedir) //
        .withConfigurationXml("<file>a.txt</file>") //
        .build(_OptionalData.class, "file").value();

    assertEquals(new File(basedir, "a.txt"), file);
  }

  @Test
  public void testOptionalFile_isDirectory() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("a.txt is a directory");

    File basedir = temp.newFolder().getCanonicalFile();
    new File(basedir, "a.txt").mkdirs();

    builder(basedir) //
        .withConfigurationXml("<file>a.txt</file>") //
        .build(_OptionalData.class, "file");
  }

  //
  //
  //

  static class _DefaultValueData {
    @InputFile(defaultValue = "1.txt")
    public File parameter;
  }

  @Test
  public void testDefaultValueInputFile() throws Exception {
    File basedir = temp.newFolder();
    File file = new File(basedir, "1.txt");
    file.createNewFile();

    InputFileValue input = builder(basedir).build(_DefaultValueData.class, "parameter");

    assertEquals(file.getCanonicalFile(), input.value());
  }

  //
  //
  //

  static class _ListFileData {
    @InputFile(defaultValue = {"1.txt", "2.txt"})
    public List<File> parameter;
  }

  @Test
  public void testMultivalueInputFile() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    new File(basedir, "1.txt").createNewFile();
    new File(basedir, "2.txt").createNewFile();

    CollectionValue input = builder(basedir).build(_ListFileData.class, "parameter");

    assertEquals(new File(basedir, "1.txt"), input.configuration.get(0).value());
    assertEquals(new File(basedir, "2.txt"), input.configuration.get(1).value());
  }

  //
  //
  //

  static class _ListFileOptionalData {
    @InputFile(fileRequired = false)
    public List<File> files;
  }

  @Test
  public void testListFileOptional_emptyConfiguration() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    CollectionValue value = builder(basedir) //
        .build(_ListFileOptionalData.class, "files");

    assertNull(value);
  }

  @Test
  public void testListFileOptional_allFilesDoNotExist() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    CollectionValue value = builder(basedir) //
        .withConfigurationXml("<files><file>1.txt</file><file>2.txt</file></files>") //
        .build(_ListFileOptionalData.class, "files");

    assertEquals(Arrays.asList(new File(basedir, "1.txt"), new File(basedir, "2.txt")),
        value.value());
  }

  @Test
  public void testListFileOptional_someFilesDoNotExist() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    new File(basedir, "1.txt").createNewFile();

    @SuppressWarnings("unchecked")
    List<File> files = (List<File>) builder(basedir) //
        .withConfigurationXml("<files><file>1.txt</file><file>2.txt</file></files>") //
        .build(_ListFileOptionalData.class, "files").value();

    assertEquals(Arrays.asList(new File(basedir, "1.txt"), new File(basedir, "2.txt")), files);
  }

  //
  //
  //

  static class _CompileSourceRoots {
    @InputFile(SOURCE_ROOTS_EXPR)
    public List<File> thisShouldFail;

    @InputFile(TEST_SOURCE_ROOTS_EXPR)
    public List<File> thisToo;
  }

  @Test
  public void testCompileSourceRootsAsInputFileFails() throws Exception {
    File dir1 = temp.newFolder();
    File dir2 = temp.newFolder();

    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(String.format(
        "_CompileSourceRoots.thisShouldFail.thisShouldFail: %s expression is not allowed here",
        SOURCE_ROOTS_EXPR));

    builder(temp.getRoot()) //
        .withCompileSourceRoot(dir1.getCanonicalPath())
        .withCompileSourceRoot(dir2.getCanonicalPath())
        .build(_CompileSourceRoots.class, "thisShouldFail");
  }

  @Test
  public void testTestCompileSourceRootsAsInputFileFails() throws Exception {
    File dir1 = temp.newFolder();
    File dir2 = temp.newFolder();

    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(
        String.format("_CompileSourceRoots.thisToo.thisToo: %s expression is not allowed here",
            TEST_SOURCE_ROOTS_EXPR));

    builder(temp.getRoot()) //
        .withTestCompileSourceRoot(dir1.getCanonicalPath())
        .withTestCompileSourceRoot(dir2.getCanonicalPath())
        .build(_CompileSourceRoots.class, "thisToo");
  }

}
