package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderInputsBuilder.SOURCE_ROOTS_EXPR;
import static io.takari.builder.internal.BuilderInputsBuilder.TEST_SOURCE_ROOTS_EXPR;
import static io.takari.builder.internal.DirAssert.assertFiles;
import static io.takari.builder.internal.TestInputBuilder.builder;
import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.IDirectoryFiles;
import io.takari.builder.InputDirectoryFiles;
import io.takari.builder.internal.BuilderInputs.CollectionValue;
import io.takari.builder.internal.BuilderInputs.InputDirectoryValue;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidConfigurationException;

public class InputDirectoryFilesTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  //
  //
  //

  static class _Data {
    @InputDirectoryFiles(filesRequired = true, defaultValue = ".", includes = "**/*.txt")
    public IDirectoryFiles files;
  }

  @Test
  public void testDirectoryFiles() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    new File(basedir, "1.txt").createNewFile();
    InputDirectoryValue input = builder(basedir).build(_Data.class, "files");
    assertEquals(basedir.getCanonicalFile(), input.location());
    assertNull(input.excludes);
    assertEquals(Collections.singleton("**/*.txt"), input.includes);
    assertFiles(input.files(), new File(basedir, "1.txt"));
  }

  @Test
  public void testDirectoryFiles_directoryDoesNotExist() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("_Data.files: is required");

    builder(new File(temp.newFolder(), "not-there")).build(_Data.class, "files");
  }

  @Test
  public void testDirectoryFiles_isFile() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("is a regular file");

    builder(temp.newFile()).build(_Data.class, "files");
  }

  @Test
  public void testDirectoryFiles_noMatchingFiles() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("_Data.files: is required");

    builder(temp.newFolder()).build(_Data.class, "files");
  }

  //
  //
  //

  static class _ListDirectoryFilesData {
    @InputDirectoryFiles(filesRequired = true, defaultValue = ".", includes = "**/*.txt")
    public List<IDirectoryFiles> directories;
  }

  @Test
  public void testInputDirectories() throws Exception {
    File dir1 = temp.newFolder().getCanonicalFile();
    new File(dir1, "1.txt").createNewFile();
    File dir2 = temp.newFolder().getCanonicalFile();
    new File(dir2, "2.txt").createNewFile();
    new File(dir2, "2.not-txt").createNewFile();

    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Xpp3Dom directories = new Xpp3Dom("directories");
    configuration.addChild(directories);
    directories.addChild(newDirectory(dir1));
    directories.addChild(newDirectory(dir2));

    CollectionValue input = builder(temp.getRoot()) //
        .withConfiguration(configuration) //
        .build(_ListDirectoryFilesData.class, "directories");

    assertEquals(2, input.configuration.size());
    assertEquals(dir1, ((InputDirectoryValue) input.configuration.get(0)).location());
    assertEquals(dir2, ((InputDirectoryValue) input.configuration.get(1)).location());
  }

  @Test
  public void testInputDirectories_someDoNotExistOrEmpty() throws Exception {
    File dir1 = temp.newFolder().getCanonicalFile();
    new File(dir1, "1.txt").createNewFile();
    File empty = temp.newFolder().getCanonicalFile();
    File notthere = new File(temp.newFolder().getCanonicalFile(), "not-there");

    Xpp3Dom configurationXml = new Xpp3Dom("configuration");
    Xpp3Dom directoriesXml = new Xpp3Dom("directories");
    configurationXml.addChild(directoriesXml);
    directoriesXml.addChild(newDirectory(dir1));
    directoriesXml.addChild(newDirectory(empty));
    directoriesXml.addChild(newDirectory(notthere));

    @SuppressWarnings("unchecked")
    List<IDirectoryFiles> directories = (List<IDirectoryFiles>) builder(temp.getRoot()) //
        .withConfiguration(configurationXml) //
        .build(_ListDirectoryFilesData.class, "directories").value();

    // at least one directory with matching files must be present
    // empty and non-existing directories are ignored in that case

    assertThat(directories).hasSize(1) //
        .extracting(d -> d.location()).element(0).isEqualTo(dir1);
  }

  @Test
  public void testInputDirectories_allDoNotExistOrEmpty() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("_ListDirectoryFilesData.directories: is required");

    File empty = temp.newFolder().getCanonicalFile();
    File notthere = new File(temp.newFolder().getCanonicalFile(), "not-there");

    Xpp3Dom configurationXml = new Xpp3Dom("configuration");
    Xpp3Dom directoriesXml = new Xpp3Dom("directories");
    configurationXml.addChild(directoriesXml);
    directoriesXml.addChild(newDirectory(empty));
    directoriesXml.addChild(newDirectory(notthere));

    builder(temp.getRoot()) //
        .withConfiguration(configurationXml) //
        .build(_ListDirectoryFilesData.class, "directories").value();
  }

  private Xpp3Dom newDirectory(File location) {
    Xpp3Dom dir = new Xpp3Dom("files");
    dir.addChild(newParameter("location", location.getAbsolutePath()));
    return dir;
  }

  @Test
  public void testMultivalueWithoutLocation_required() throws Exception {
    // each configured directory must have location attribute

    thrown.expect(InvalidConfigurationException.class);

    builder(temp.getRoot()) //
        .withConfigurationXml(
            "<directories><files><excludes><e>e.txt</e></excludes></files></directories>") //
        .build(_ListDirectoryFilesData.class, "directories");

  }

  //
  //
  //

  static class _OptionalData {
    @InputDirectoryFiles(filesRequired = false, includes = "**/*")
    public IDirectoryFiles directory;

    @InputDirectoryFiles(filesRequired = false, includes = "**/*")
    public List<IDirectoryFiles> directories;
  }

  @Test
  public void testOptional() throws Exception {
    File basedir = temp.newFolder();
    new File(basedir, "1.txt").createNewFile();

    assertNull(builder(basedir).build(_OptionalData.class, "directory"));
    assertNull(builder(basedir).build(_OptionalData.class, "directories"));
  }

  @Test
  public void testOptional_isFile() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("is a regular file");

    builder(temp.newFile()) //
        .withConfigurationXml("<directory>.</directory>") //
        .build(_OptionalData.class, "directory");
  }

  @Test
  public void testMultivalueWithoutLocation_optional() throws Exception {
    // each configured directory must have location attribute

    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("<location> element is required");

    builder(temp.getRoot()) //
        .withConfigurationXml(
            "<directories><files><excludes><e>e.txt</e></excludes></files></directories>") //
        .build(_OptionalData.class, "directories");
  }

  @Test
  public void testOptional_directoryDoesNotExist() throws Exception {
    File basedir = temp.getRoot().getCanonicalFile();

    IDirectoryFiles files = (IDirectoryFiles) builder(basedir) //
        .withConfigurationXml("<directory>not-there</directory>") //
        .build(_OptionalData.class, "directory").value();

    // optional DirectoryFile is created when directory does not exist

    assertThat(files.location()).doesNotExist();
    assertThat(files.location()).isEqualTo(new File(basedir, "not-there"));
    assertThat(files.files()).isEmpty();
  }

  @Test
  public void testOptional_directoriesDoNotExist() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    @SuppressWarnings("unchecked")
    List<IDirectoryFiles> directories = (List<IDirectoryFiles>) builder(basedir) //
        .withConfigurationXml("<directories><location>not-there</location></directories>") //
        .build(_OptionalData.class, "directories").value();

    assertThat(directories).hasSize(1);
    assertThat(directories.get(0).location()).doesNotExist();
    assertThat(directories.get(0).location()).isEqualTo(new File(basedir, "not-there"));
    assertThat(directories.get(0).files()).isEmpty();
  }

  //
  //
  //

  static class _ListFileData {
    @InputDirectoryFiles(filesRequired = true, defaultValue = ".", includes = "**/*.txt")
    List<File> files;
  }

  @Test
  public void testListFile() throws Exception {
    File dir1 = temp.newFolder().getCanonicalFile();
    new File(dir1, "1.txt").createNewFile();
    new File(dir1, "2.txt").createNewFile();

    @SuppressWarnings("unchecked")
    List<File> files = (List<File>) builder(temp.getRoot()) //
        .build(_ListFileData.class, "files") //
        .value();

    assertThat(files).hasSize(2);
    assertThat(files.get(0)).isInstanceOf(File.class);
  }

  @Test
  public void testListFile_requiredEmpty() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(".files: is required");

    builder(temp.newFolder()).build(_ListFileData.class, "files");
  }

  static class _ArrayFileData {
    @InputDirectoryFiles(defaultValue = ".", includes = "**/*.txt")
    File[] files;
  }

  @Test
  public void testArrayFile() throws Exception {
    File dir1 = temp.newFolder().getCanonicalFile();
    new File(dir1, "1.txt").createNewFile();
    new File(dir1, "2.txt").createNewFile();

    File[] files = (File[]) builder(temp.getRoot()) //
        .build(_ArrayFileData.class, "files") //
        .value();

    assertThat(files).hasSize(2);
    assertThat(files).isInstanceOf(File[].class);
  }

  //
  //
  //

  static class _ListPathData {
    @InputDirectoryFiles(filesRequired = true, defaultValue = ".", includes = "**/*.txt")
    List<Path> files;
  }

  @Test
  public void testListPath() throws Exception {
    File dir1 = temp.newFolder().getCanonicalFile();
    new File(dir1, "1.txt").createNewFile();
    new File(dir1, "2.txt").createNewFile();

    @SuppressWarnings("unchecked")
    List<Path> files = (List<Path>) builder(temp.getRoot()) //
        .build(_ListPathData.class, "files") //
        .value();

    assertThat(files).hasSize(2);
    assertThat(Path.class).isAssignableFrom(files.get(0).getClass());
  }

  //
  //
  //

  static class _NoIncludesData {
    @InputDirectoryFiles(defaultValue = ".")
    public IDirectoryFiles files;
  }

  @Test
  public void testNoIncludes() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("<includes> is required");

    File basedir = temp.newFolder();
    new File(basedir, "1.txt").createNewFile();
    builder(basedir).build(_NoIncludesData.class, "files");
  }

  //
  //
  //

  static class _CompileSourceRoots {
    @InputDirectoryFiles(filesRequired = true, value = SOURCE_ROOTS_EXPR, includes = "**/*")
    public List<IDirectoryFiles> compileSourceRootFiles;

    @InputDirectoryFiles(filesRequired = true, value = {SOURCE_ROOTS_EXPR,
        "/bad"}, includes = "**/*")
    public List<IDirectoryFiles> compileSourceRootBadFiles;

    @InputDirectoryFiles(filesRequired = true, value = TEST_SOURCE_ROOTS_EXPR, includes = "**/*")
    public List<IDirectoryFiles> testCompileSourceRootFiles;

    @InputDirectoryFiles(filesRequired = true, value = {TEST_SOURCE_ROOTS_EXPR,
        "/bad"}, includes = "**/*")
    public List<IDirectoryFiles> testCompileSourceRootBadFiles;
  }

  @Test
  public void testCompileSourceRoots() throws Exception {
    File dir1 = temp.newFolder();
    File dir2 = temp.newFolder();
    File file = new File(dir1, "file.txt");

    file.createNewFile();

    CollectionValue input = builder(temp.getRoot()) //
        .withCompileSourceRoot(dir1.getCanonicalPath())
        .withCompileSourceRoot(dir2.getCanonicalPath())
        .build(_CompileSourceRoots.class, "compileSourceRootFiles");

    assertThat(input.configuration).hasSize(1);
    assertEquals(dir1.getCanonicalPath(),
        ((IDirectoryFiles) input.configuration.get(0)).location().getCanonicalPath());
  }

  @Test
  public void testTestCompileSourceRoots() throws Exception {
    File dir1 = temp.newFolder();
    File dir2 = temp.newFolder();
    File file = new File(dir1, "file.txt");

    file.createNewFile();

    CollectionValue input = builder(temp.getRoot()) //
        .withTestCompileSourceRoot(dir1.getCanonicalPath())
        .withTestCompileSourceRoot(dir2.getCanonicalPath())
        .build(_CompileSourceRoots.class, "testCompileSourceRootFiles");

    assertThat(input.configuration).hasSize(1);
    assertEquals(dir1.getCanonicalPath(),
        ((IDirectoryFiles) input.configuration.get(0)).location().getCanonicalPath());
  }

  @Test
  public void testCompileSourceRootsExtraParams() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(
        String.format("%s can not have other values provided along with it", SOURCE_ROOTS_EXPR));

    File dir1 = temp.newFolder();
    File dir2 = temp.newFolder();

    builder(temp.getRoot()) //
        .withCompileSourceRoot(dir1.getCanonicalPath())
        .withCompileSourceRoot(dir2.getCanonicalPath())
        .build(_CompileSourceRoots.class, "compileSourceRootBadFiles");
  }

  @Test
  public void testTestCompileSourceRootsExtraParams() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(String.format("%s can not have other values provided along with it",
        TEST_SOURCE_ROOTS_EXPR));

    File dir1 = temp.newFolder();
    File dir2 = temp.newFolder();

    builder(temp.getRoot()) //
        .withTestCompileSourceRoot(dir1.getCanonicalPath())
        .withTestCompileSourceRoot(dir2.getCanonicalPath())
        .build(_CompileSourceRoots.class, "testCompileSourceRootBadFiles");
  }

  @Test
  public void testRequiredCompileSourceRootFailsWithoutSourceRoots() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("_CompileSourceRoots.compileSourceRootFiles: is required");

    builder(temp.getRoot()) //
        .build(_CompileSourceRoots.class, "compileSourceRootFiles");
  }

  @Test
  public void testConfiguredCompileSourceRootsFails() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(String.format("_Data.files: %s expression is not allowed in configuration",
        SOURCE_ROOTS_EXPR));

    File dir1 = temp.newFolder();
    File dir2 = temp.newFolder();

    builder(temp.getRoot()) //
        .withCompileSourceRoot(dir1.getCanonicalPath())
        .withCompileSourceRoot(dir2.getCanonicalPath()).withConfigurationXml(String
            .format("<files><files><location>%s</location></files></files>", SOURCE_ROOTS_EXPR))
        .build(_Data.class, "files");
  }

  @Test
  public void testConfiguredTestCompileSourceRootsFails() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(String.format("_Data.files: %s expression is not allowed in configuration",
        TEST_SOURCE_ROOTS_EXPR));

    File dir1 = temp.newFolder();
    File dir2 = temp.newFolder();

    builder(temp.getRoot()) //
        .withTestCompileSourceRoot(dir1.getCanonicalPath())
        .withTestCompileSourceRoot(dir2.getCanonicalPath())
        .withConfigurationXml(String.format("<files><files><location>%s</location></files></files>",
            TEST_SOURCE_ROOTS_EXPR))
        .build(_Data.class, "files");
  }

  //
  //
  //

  static class _ArraySpecificFileData {
    @InputDirectoryFiles(defaultValue = ".", includes = "1.txt")
    File[] files;
  }

  @Test
  public void testSpecificFile() throws Exception {
    File dir = temp.newFolder().getCanonicalFile();
    File file = new File(dir, "1.txt");
    file.createNewFile();
    new File(dir, "2.txt").createNewFile();

    File[] files = (File[]) builder(dir) //
        .build(_ArraySpecificFileData.class, "files") //
        .value();

    assertThat(files).containsExactly(file);
  }

}
