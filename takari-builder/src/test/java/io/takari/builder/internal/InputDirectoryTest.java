package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderInputs.digest;
import static io.takari.builder.internal.BuilderInputsBuilder.SOURCE_ROOTS_EXPR;
import static io.takari.builder.internal.BuilderInputsBuilder.TEST_SOURCE_ROOTS_EXPR;
import static io.takari.builder.internal.DirAssert.assertFiles;
import static io.takari.builder.internal.TestInputBuilder.builder;
import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.InputDirectory;
import io.takari.builder.internal.BuilderInputs.CollectionValue;
import io.takari.builder.internal.BuilderInputs.Digest;
import io.takari.builder.internal.BuilderInputs.InputDirectoryValue;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidConfigurationException;


public class InputDirectoryTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  //
  //
  //

  static class _Data {
    @InputDirectory(filesRequired = true, defaultValue = ".", includes = "**/*.txt")
    public File directory;
  }

  @Test
  public void testInputDirectory() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    new File(basedir, "1.txt").createNewFile();
    InputDirectoryValue input = builder(basedir).build(_Data.class, "directory");
    assertEquals(basedir.getCanonicalFile(), input.value());
    assertNull(input.excludes);
    assertEquals(Collections.singleton("**/*.txt"), input.includes);
    assertFiles(input.files(), new File(basedir, "1.txt"));

    BuilderInputs inputs = builder(basedir).build(_Data.class);
    assertFiles(pathToFile(inputs.inputFiles), new File(basedir, "1.txt"));
  }

  @Test
  public void testInputDirectory_noMatchingFiles() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("_Data.directory: is required");

    File basedir = temp.newFolder().getCanonicalFile();
    new File(basedir, "data.bin").createNewFile(); // does not match includes pattern

    builder(basedir).build(_Data.class, "directory");
  }

  @Test
  public void testInputDirectory_isFile() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("is a regular file");

    builder(temp.newFile()).build(_Data.class, "directory");
  }

  @Test
  public void testInputDirectory_doesNotExist() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("_Data.directory: is required");

    File basedir = new File(temp.newFolder(), "nothing");
    InputDirectoryValue input = builder(basedir).build(_Data.class, "directory");
    assertEquals(basedir.getCanonicalFile(), input.location);
    assertNull(input.excludes);
    assertEquals(Collections.singleton("**/*.txt"), input.includes);
    assertFiles(input.files);
  }

  //
  //
  //

  static class _PathData {
    @InputDirectory(filesRequired = true, defaultValue = ".", includes = "**/*.txt")
    public Path directory;
  }

  @Test
  public void testPathInputDirectory() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    Path path = Paths.get(basedir.getCanonicalPath());
    new File(basedir, "1.txt").createNewFile();
    InputDirectoryValue input = builder(basedir).build(_PathData.class, "directory");
    assertEquals(path, input.value());
    assertNull(input.excludes);
    assertEquals(Collections.singleton("**/*.txt"), input.includes);
    assertFiles(input.files(), new File(basedir, "1.txt"));

    BuilderInputs inputs = builder(basedir).build(_PathData.class);
    assertFiles(pathToFile(inputs.inputFiles), new File(basedir, "1.txt"));
  }

  //
  //
  //

  static class _ListFileData {
    @InputDirectory(defaultValue = ".", includes = "**/*.txt")
    public List<File> directories;
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
        .build(_ListFileData.class, "directories");

    assertEquals(2, input.configuration.size());
    assertEquals(dir1, ((InputDirectoryValue) input.configuration.get(0)).value());
    assertEquals(dir2, ((InputDirectoryValue) input.configuration.get(1)).value());
  }

  private Xpp3Dom newDirectory(File location) {
    Xpp3Dom dir = new Xpp3Dom("directory");
    dir.addChild(newParameter("location", location.getAbsolutePath()));
    return dir;
  }

  @Test
  public void testInputDirectories_noLocationConfiguration() throws Exception {
    // each configured directory must have location attribute

    thrown.expect(InvalidConfigurationException.class);

    builder(temp.getRoot()) //
        .withConfigurationXml("<directories><d><excludes><e>e.txt</e></excludes></d></directories>") //
        .build(_ListFileData.class, "directories");
  }

  //
  //
  //

  static class _OptionalData {
    @InputDirectory(filesRequired = false, includes = "**/*")
    public File directory;

    @InputDirectory(filesRequired = false, includes = "**/*")
    public List<File> directories;
  }

  @Test
  public void testOptional_noConfiguration() throws Exception {
    File basedir = temp.newFolder();
    new File(basedir, "1.txt").createNewFile();

    assertNull(builder(basedir).build(_OptionalData.class, "directory"));
    assertNull(builder(basedir).build(_OptionalData.class, "directories"));
  }

  @Test
  public void testOptionalInputDirectories_noLocationConfiguration() throws Exception {
    // each configured directory must have location attribute

    thrown.expect(InvalidConfigurationException.class);
    // TODO assert message _OptionalData.directories[0].location attribute value is required.

    builder(temp.getRoot()) //
        .withConfigurationXml("<directories><d><excludes><e>e.txt</e></excludes></d></directories>") //
        .build(_OptionalData.class, "directories");
  }

  @Test
  public void testOptional_directoryDoesNotExist() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    File dir1 = new File(basedir, "not-there-1");
    File dir2 = new File(basedir, "not-there-2");

    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Xpp3Dom directories = new Xpp3Dom("directories");
    configuration.addChild(directories);
    directories.addChild(newDirectory(dir1));
    directories.addChild(newDirectory(dir2));

    CollectionValue input = builder(basedir) //
        .withConfiguration(configuration) //
        .build(_OptionalData.class, "directories");

    assertEquals(2, input.configuration.size());
    assertEquals(dir1, ((InputDirectoryValue) input.configuration.get(0)).value());
    assertEquals(dir2, ((InputDirectoryValue) input.configuration.get(1)).value());

    // also check missing directories are not considered "input files"

    BuilderInputs inputs = builder(basedir) //
        .withConfiguration(configuration) //
        .build(_OptionalData.class);

    assertTrue(inputs.inputFiles.isEmpty());
  }

  //
  //
  //

  static InputDirectoryValue newInputDirectoryFiles(Path location, String includes,
      String excludes) {
    return new InputDirectoryValue(File.class, location, Collections.singletonList(includes),
        Collections.singletonList(excludes), Collections.emptySet(), Collections.emptySet());
  }

  @Test
  public void testDigest() throws Exception {
    Digest digest = digest(newInputDirectoryFiles(new File("a").toPath(), "i", "e"));

    assertEquals(digest, digest(newInputDirectoryFiles(new File("a").toPath(), "i", "e")));
    assertNotEquals(digest, digest(newInputDirectoryFiles(new File("b").toPath(), "i", "e")));
  }

  //
  //
  //

  static class _CompileSourceRoots {
    @InputDirectory(filesRequired = true, value = SOURCE_ROOTS_EXPR, includes = "**/*")
    public List<File> compileSourceRoots;

    @InputDirectory(filesRequired = true, value = {SOURCE_ROOTS_EXPR, "/bad"}, includes = "**/*")
    public List<File> badCompileSourceRoots;

    @InputDirectory(filesRequired = true, value = TEST_SOURCE_ROOTS_EXPR, includes = "**/*")
    public List<File> testCompileSourceRoots;

    @InputDirectory(filesRequired = true, value = {"/bad",
        TEST_SOURCE_ROOTS_EXPR}, includes = "**/*")
    public List<File> badTestCompileSourceRoots;
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
        .build(_CompileSourceRoots.class, "compileSourceRoots");

    assertThat(input.configuration).hasSize(1);
    assertEquals(dir1.getCanonicalPath(),
        ((InputDirectoryValue) input.configuration.get(0)).location.toRealPath().toString());
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
        .build(_CompileSourceRoots.class, "testCompileSourceRoots");

    assertThat(input.configuration).hasSize(1);
    assertEquals(dir1.getCanonicalPath(),
        ((InputDirectoryValue) input.configuration.get(0)).location.toRealPath().toString());
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
        .build(_CompileSourceRoots.class, "badCompileSourceRoots");
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
        .build(_CompileSourceRoots.class, "badTestCompileSourceRoots");
  }

  @Test
  public void testRequiredCompileSourceRootFailsWithoutSourceRoots() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("_CompileSourceRoots.compileSourceRoots.compileSourceRoots: is required");

    builder(temp.getRoot()) //
        .build(_CompileSourceRoots.class, "compileSourceRoots");
  }

  @Test
  public void testConfiguredCompileSourceRootsFails() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(String.format(
        "_Data.directory: %s expression is not allowed in configuration", SOURCE_ROOTS_EXPR));

    File dir1 = temp.newFolder();
    File dir2 = temp.newFolder();

    builder(temp.getRoot()) //
        .withCompileSourceRoot(dir1.getCanonicalPath())
        .withCompileSourceRoot(dir2.getCanonicalPath())
        .withConfigurationXml(
            String.format("<directory><location>%s</location></directory>", SOURCE_ROOTS_EXPR))
        .build(_Data.class, "directory");
  }

  @Test
  public void testConfiguredTestCompileSourceRootsFails() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(String.format(
        "_Data.directory: %s expression is not allowed in configuration", TEST_SOURCE_ROOTS_EXPR));

    File dir1 = temp.newFolder();
    File dir2 = temp.newFolder();

    builder(temp.getRoot()) //
        .withCompileSourceRoot(dir1.getCanonicalPath())
        .withCompileSourceRoot(dir2.getCanonicalPath())
        .withConfigurationXml(
            String.format("<directory><location>%s</location></directory>", TEST_SOURCE_ROOTS_EXPR))
        .build(_Data.class, "directory");
  }

  private Set<File> pathToFile(Set<Path> files) {
    return files.stream().map(file -> file.toFile()).collect(Collectors.toSet());
  }
}
