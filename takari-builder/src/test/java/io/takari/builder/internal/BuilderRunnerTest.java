package io.takari.builder.internal;

import static io.takari.maven.testing.TestResources.assertDirectoryContents;
import static io.takari.maven.testing.TestResources.create;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableList;

import io.takari.builder.Builder;
import io.takari.builder.Dependencies;
import io.takari.builder.GeneratedResourcesDirectory;
import io.takari.builder.GeneratedSourcesDirectory;
import io.takari.builder.IArtifactMetadata;
import io.takari.builder.InputDirectory;
import io.takari.builder.InputFile;
import io.takari.builder.NonDeterministic;
import io.takari.builder.OutputDirectory;
import io.takari.builder.OutputFile;
import io.takari.builder.Parameter;
import io.takari.builder.ResolutionScope;
import io.takari.builder.ResourceType;
import io.takari.builder.enforcer.internal.EnforcerConfig;
import io.takari.builder.internal.utils.JarBuilder;
import io.takari.builder.testing.BuilderExecution;
import io.takari.builder.testing.BuilderExecutionException;
import io.takari.builder.testing.BuilderRuntime;
import io.takari.builder.testing.InternalBuilderExecution;
import io.takari.maven.testing.TestResources;

public class BuilderRunnerTest {

  public final BuilderRuntime runtime = new BuilderRuntime();

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private InternalBuilderExecution builderExecution(Class<?> builder) throws IOException {
    return InternalBuilderExecution.builderExecution(temp.newFolder(), builder);
  }

  //
  //
  //

  static class StaticInitializationBuilder {

    public static final File TEMP;

    static {
      File temp = null;
      try {
        String key = StaticInitializationBuilder.class.getName();
        temp = new File(System.getProperty(key), "tmp-file");
        temp.createNewFile();
      } catch (IOException e) {
        // too bad
      }
      TEMP = temp;
    }

    @Builder(name = "static-initialization")
    public void execute() {}
  }

  @Test(expected = SecurityException.class)
  public void testBuilderStaticInitializationEnforcement() throws Exception {
    // assert resource access policies are enforced during builder static initialization

    File tempdir = temp.newFolder();
    System.setProperty(StaticInitializationBuilder.class.getName(), tempdir.getCanonicalPath());

    BuilderExecution.builderExecution(tempdir, StaticInitializationBuilder.class).execute();
  }

  //
  //
  //

  static class CounterBuilder {
    static final AtomicInteger COUNTER = new AtomicInteger();

    @Parameter(defaultValue = "1")
    int step;

    @Builder(name = "static-counter")
    public void count() {
      COUNTER.addAndGet(step);
    }
  }

  @SuppressWarnings("serial")
  static class CounterFailingException extends RuntimeException {

  }

  static class CounterFailingBuilder {
    static final AtomicInteger COUNTER = new AtomicInteger();

    @Parameter(defaultValue = "1")
    int step;

    @Builder(name = "static-failing-counter")
    public void count() throws CounterFailingException {
      COUNTER.addAndGet(step);

      throw new CounterFailingException();
    }
  }

  @Test
  public void testBasicIncrementalBuild() throws Exception {
    File basedir = temp.newFolder();
    File stateFile = temp.newFile();
    CounterBuilder.COUNTER.set(0);

    //
    // initial build
    //

    InternalBuilderExecution.builderExecution(basedir, CounterBuilder.class) //
        .withStateFile(stateFile)//
        .execute();
    assertEquals(1, CounterBuilder.COUNTER.get());

    //
    // no-change rebuild
    //

    InternalBuilderExecution.builderExecution(basedir, CounterBuilder.class) //
        .withStateFile(stateFile)//
        .execute();
    assertEquals(1, CounterBuilder.COUNTER.get());

    //
    // configuration change rebuild
    //

    InternalBuilderExecution.builderExecution(basedir, CounterBuilder.class) //
        .withStateFile(stateFile)//
        .withConfiguration("step", "2") //
        .execute();
    assertEquals(3, CounterBuilder.COUNTER.get());
  }

  @Test
  public void testFailingIncrementalBuild() throws Exception {
    File basedir = temp.newFolder();
    File stateFile = temp.newFile();
    CounterFailingBuilder.COUNTER.set(0);

    //
    // initial build
    //

    try {
      InternalBuilderExecution.builderExecution(basedir, CounterFailingBuilder.class) //
          .withStateFile(stateFile)//
          .execute();
      fail();
    } catch (BuilderExecutionException expected) {
      // TODO assert error message
    }
    assertEquals(1, CounterFailingBuilder.COUNTER.get());

    //
    // no-change rebuild
    //

    try {
      InternalBuilderExecution.builderExecution(basedir, CounterFailingBuilder.class) //
          .withStateFile(stateFile)//
          .execute();
      fail();
    } catch (BuilderExecutionException expected) {
      // TODO assert error message
    }
    assertEquals(1, CounterFailingBuilder.COUNTER.get());

    //
    // configuration change rebuild
    //

    try {
      InternalBuilderExecution.builderExecution(basedir, CounterFailingBuilder.class) //
          .withStateFile(stateFile)//
          .withConfiguration("step", "2") //
          .execute();
      fail();
    } catch (BuilderExecutionException expected) {
      // TODO assert error message
    }
    assertEquals(3, CounterFailingBuilder.COUNTER.get());
  }

  @Test
  public void testIncrementalBuildClasspathChange() throws Exception {
    File basedir = temp.newFolder();
    File stateFile = temp.newFile();

    File jar = JarBuilder.create(temp.newFile().getCanonicalFile()) //
        .withEntry("Class1.class", "Class1") //
        .build();
    File dir = temp.newFolder().getCanonicalFile();
    File classfile = new File(dir, "Class2.class");
    Files.write(classfile.toPath(), "Class2".getBytes());

    List<File> classpath = ImmutableList.of(jar, dir);

    CounterBuilder.COUNTER.set(0);

    // initial build
    InternalBuilderExecution.builderExecution(basedir, CounterBuilder.class) //
        .withStateFile(stateFile)//
        .withClasspath(classpath) //
        .execute();
    assertEquals(1, CounterBuilder.COUNTER.get());

    // classes directory entry change rebuild
    Files.write(classfile.toPath(), "Class2 changed".getBytes());
    InternalBuilderExecution.builderExecution(basedir, CounterBuilder.class) //
        .withStateFile(stateFile)//
        .withClasspath(classpath) //
        .execute();
    assertEquals(2, CounterBuilder.COUNTER.get());

    // jar file change rebuild
    JarBuilder.create(jar).withEntry("Class1.class", "Class1 changed").build();
    InternalBuilderExecution.builderExecution(basedir, CounterBuilder.class) //
        .withStateFile(stateFile)//
        .withClasspath(classpath) //
        .execute();
    assertEquals(3, CounterBuilder.COUNTER.get());

    // sanity check, no-change rebuild
    InternalBuilderExecution.builderExecution(basedir, CounterBuilder.class) //
        .withStateFile(stateFile)//
        .withClasspath(classpath) //
        .execute();
    assertEquals(3, CounterBuilder.COUNTER.get());
  }

  //
  //
  //

  static class CopyFilesBuilder {

    @InputFile
    List<File> files;

    @OutputDirectory
    File directory;

    @Builder(name = "copy-files")
    public void copy() throws IOException {
      for (File file : files) {
        File outputFile = new File(directory, file.getName());
        if (!outputFile.isFile() || outputFile.length() != file.length()
            || outputFile.lastModified() != file.lastModified()) {
          Files.copy(file.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  @Test
  public void testCleanupObsoleteOutput() throws Exception {
    File basedir = temp.newFolder();
    File stateFile = temp.newFile();
    create(basedir, "a", "b");
    File target = temp.newFolder();

    //
    // initial build
    //

    InternalBuilderExecution.builderExecution(basedir, CopyFilesBuilder.class) //
        .withStateFile(stateFile) //
        .withConfiguration("directory", target.getCanonicalPath()) //
        .withConfiguration("files", Arrays.asList("a", "b")) //
        .execute();
    assertDirectoryContents(target, "a", "b");

    //
    // configuration to only copy one file
    //

    InternalBuilderExecution.builderExecution(basedir, CopyFilesBuilder.class) //
        .withStateFile(stateFile)//
        .withConfiguration("directory", target.getCanonicalPath()) //
        .withConfiguration("files", Arrays.asList("a")) //
        .execute();
    assertDirectoryContents(target, "a");
  }

  static class InputOutputBuilder {

    @Parameter(required = true)
    boolean write;

    @OutputDirectory(required = true)
    File dir;

    @NonDeterministic
    @Builder(name = "input-output")
    // NOTE: this builder is intentionally non-idempotent in order to test the ability
    // to write to inputs - This is here to support a specific test case
    // It is not recommended to write builders like this
    public void execute() throws IOException {

      if (write) {
        incrementFile(new File(dir, "a"));
      }
    }

    private void incrementFile(File file) throws IOException, FileNotFoundException {
      int current;

      try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
        current = Character.getNumericValue(reader.read());
      }
      try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
        writer.write(Character.forDigit((current + 1), 10));
      }
    }
  }

  @Test
  public void testInputAsOutput() throws Exception {
    File basedir = temp.newFolder();
    File stateFile = temp.newFile();
    File target = new File(basedir, "out");
    File file = new File(target, "a");
    String relpath = basedir.toPath().relativize(file.toPath()).toString();

    EnforcerConfig enforcerConfig = EnforcerConfig.builder().enforce(true)
        .withReadAndTrackException("input-output", relpath).build();

    file.getParentFile().mkdirs();
    file.createNewFile();

    //
    // initial build
    //

    InternalBuilderExecution.builderExecution(basedir, InputOutputBuilder.class) //
        .withStateFile(stateFile) //
        .withConfiguration("dir", target.getCanonicalPath()) //
        .withConfiguration("write", "true") //
        .withEnforcerConfig(enforcerConfig) //
        .execute();

    assertThat(readFile(file)).as("File should have been written to once").isEqualTo("0");

    //
    // manually edit file, builder should recognize changed input and update
    Files.write(file.toPath(), "1".getBytes());
    TestResources.touch(file);

    InternalBuilderExecution.builderExecution(basedir, InputOutputBuilder.class) //
        .withStateFile(stateFile) //
        .withConfiguration("dir", target.getCanonicalPath()) //
        .withConfiguration("write", "true") //
        .withEnforcerConfig(enforcerConfig) //
        .execute();

    assertThat(readFile(file)).as("File should exist and have been written to").isEqualTo("2");

    //
    // don't edit file, but change inputs. File should still exist

    InternalBuilderExecution.builderExecution(basedir, InputOutputBuilder.class) //
        .withStateFile(stateFile) //
        .withConfiguration("dir", target.getCanonicalPath()) //
        .withConfiguration("write", "false") //
        .withEnforcerConfig(enforcerConfig) //
        .execute();

    assertThat(readFile(file)).as("File should exist and have been written to").isEqualTo("2");

  }

  static class UntrackedReadBuilder {

    static final AtomicInteger COUNTER = new AtomicInteger();

    @Parameter
    String file;

    @NonDeterministic
    @Builder(name = "untracked-read")
    // NOTE: this builder is intentionally non-deterministic in order to test the ability
    // to read files from a whitelist. Do not create builders like this.
    public void execute() throws IOException {
      try (BufferedReader reader = new BufferedReader(new FileReader(new File(file)))) {
        COUNTER.incrementAndGet();
      }
    }
  }

  @Test
  public void testUntrackedRead() throws Exception {
    File basedir = temp.newFolder();
    File stateFile = temp.newFile();
    File file = temp.newFile("b");

    EnforcerConfig enforcerConfig = EnforcerConfig.builder().enforce(true).withReadException("untracked-read", "**/b")
        .build();

    //
    // initial build
    //

    InternalBuilderExecution.builderExecution(basedir, UntrackedReadBuilder.class) //
        .withStateFile(stateFile) //
        .withConfiguration("file", file.getCanonicalPath()) //
        .withEnforcerConfig(enforcerConfig) //
        .execute();

    assertThat(UntrackedReadBuilder.COUNTER.get()).as("File should have been read").isEqualTo(1);

    //
    // manually edit file, builder should not recognize changed input
    Files.write(file.toPath(), "1".getBytes());
    TestResources.touch(file);

    InternalBuilderExecution.builderExecution(basedir, UntrackedReadBuilder.class) //
        .withStateFile(stateFile) //
        .withConfiguration("file", file.getCanonicalPath()) //
        .withEnforcerConfig(enforcerConfig) //
        .execute();

    assertThat(UntrackedReadBuilder.COUNTER.get()).as("Counter should not have been incremented").isEqualTo(1);
  }

  @Test
  public void testInputAsOutputNotWhitelisted() throws Exception {

    File basedir = temp.newFolder();
    File stateFile = temp.newFile();
    File target = temp.newFolder("out");
    File file = new File(target, "a");

    file.createNewFile();

    thrown.expect(SecurityException.class);
    thrown.expectMessage("Access to an undeclared resource detected");
    thrown.expectMessage(String.format("R file:%s", file.getCanonicalPath()));
    thrown.expectMessage(String.format("W file:%s", file.getCanonicalPath()));

    //
    // initial build
    //

    InternalBuilderExecution.builderExecution(basedir, InputOutputBuilder.class) //
        .withStateFile(stateFile) //
        .withConfiguration("dir", target.getCanonicalPath()) //
        .withConfiguration("write", "true") //
        .execute();
  }

  static String readFile(File file) throws IOException {
    byte[] encoded = Files.readAllBytes(Paths.get(file.getCanonicalPath()));
    return new String(encoded, Charset.defaultCharset());
  }

  //
  //
  //

  public static final String PROPERTY = "some.property";

  static class SystemPropertiesBuilder {
    static final AtomicInteger COUNTER = new AtomicInteger();

    @Builder(name = "system-properties")
    public void accessSystemProperty() {
      System.getProperty(PROPERTY);
      COUNTER.incrementAndGet();
    }
  }

  @Test
  public void testSystemProperties() throws Exception {
    File stateFile = temp.newFile();
    SystemPropertiesBuilder.COUNTER.set(0);
    System.setProperty(PROPERTY, "value");

    builderExecution(SystemPropertiesBuilder.class).withStateFile(stateFile)//
        .execute();

    assertEquals(1, SystemPropertiesBuilder.COUNTER.get());

    // same property value, builder does is not executed
    builderExecution(SystemPropertiesBuilder.class).withStateFile(stateFile)//
        .execute();

    assertEquals(1, SystemPropertiesBuilder.COUNTER.get());

    // change property value, builder is executed
    System.setProperty(PROPERTY, "changed-value");
    builderExecution(SystemPropertiesBuilder.class).withStateFile(stateFile)//
        .execute();

    assertEquals(2, SystemPropertiesBuilder.COUNTER.get());

    // same property value, builder does is not executed
    builderExecution(SystemPropertiesBuilder.class).withStateFile(stateFile)//
        .execute();

    assertEquals(2, SystemPropertiesBuilder.COUNTER.get());
  }

  //
  //
  //

  static final AtomicReference<File> FILE = new AtomicReference<>();

  static class TemporaryFileBuilder {
    @Builder(name = "temporary-file")
    public void createTemporaryFile() throws IOException {
      Path file = Files.createTempFile("temporary", ".tmp");
      // File file = File.createTempFile("temporary", ".tmp");
      Files.exists(file); // this triggers readCheck, which is expected to succeed
      FILE.set(file.toFile());
    }
  }

  @Test
  public void testTemporaryFile() throws Exception {
    FILE.set(null);

    builderExecution(TemporaryFileBuilder.class).execute();

    assertNotNull(FILE.get());
    assertFalse(FILE.get().exists());
  }

  //
  //
  //

  static class GeneratedResourcesBuilder {
    @GeneratedResourcesDirectory(defaultValue = "main", includes = "**/*.main", defaultExcludes = "**/*.main.exclude")
    private File main;

    @GeneratedResourcesDirectory(defaultValue = "test", type = ResourceType.TEST)
    private File test;

    @Builder(name = "generate")
    public void generate() throws IOException {
      new File(main, "main.txt").createNewFile();
      new File(test, "test.txt").createNewFile();
    }
  }

  @Test
  public void testGeneratedResources() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    ArrayList<String> includes = new ArrayList<String>();
    includes.add("**/*.main");
    ArrayList<String> excludes = new ArrayList<String>();
    excludes.add("**/*.main.exclude");

    BuilderExecution.builderExecution(basedir, GeneratedResourcesBuilder.class) //
        .execute() //
        .assertProjectResources(
            new ResourceRoot(new File(basedir, "main").getAbsolutePath(), ResourceType.MAIN, includes, excludes),
            new ResourceRoot(new File(basedir, "test").getAbsolutePath(), ResourceType.TEST, new ArrayList<>(),
                new ArrayList<>())) //
        .assertOutputFiles(basedir, "main/main.txt", "test/test.txt");
  }

  @Test
  public void testGeneratedResources_incremental() throws Exception {
    // assert that resources roots are injected in the project model during incremental build

    File basedir = temp.newFolder().getCanonicalFile();
    File stateFile = temp.newFile();

    ArrayList<String> includes = new ArrayList<String>();
    includes.add("**/*.main");
    ArrayList<String> excludes = new ArrayList<String>();
    excludes.add("**/*.main.exclude");

    // initial build
    InternalBuilderExecution.builderExecution(basedir, GeneratedResourcesBuilder.class) //
        .withStateFile(stateFile) //
        .execute() //
        .assertProjectResources(
            new ResourceRoot(new File(basedir, "main").getAbsolutePath(), ResourceType.MAIN, includes, excludes),
            new ResourceRoot(new File(basedir, "test").getAbsolutePath(), ResourceType.TEST, new ArrayList<>(),
                new ArrayList<>())) //
        .assertOutputFiles(basedir, "main/main.txt", "test/test.txt");

    // incremental no-change build
    InternalBuilderExecution.builderExecution(basedir, GeneratedResourcesBuilder.class) //
        .withStateFile(stateFile) //
        .execute() //
        .assertProjectResources(
            new ResourceRoot(new File(basedir, "main").getAbsolutePath(), ResourceType.MAIN, includes, excludes),
            new ResourceRoot(new File(basedir, "test").getAbsolutePath(), ResourceType.TEST, new ArrayList<>(),
                new ArrayList<>())) //
        .assertOutputFiles(basedir, new String[0]); // nothing generated during this build

    // assert generated files are still there
    assertDirectoryContents(basedir, "main/", "main/main.txt", "test/", "test/test.txt");
  }

  //
  //
  //

  static class GeneratedSourcesBuilder {
    @GeneratedSourcesDirectory(defaultValue = "main")
    private File main;

    @GeneratedSourcesDirectory(defaultValue = "test", sourceType = ResourceType.TEST)
    private File test;

    @Builder(name = "generate")
    public void generate() throws IOException {
      new File(main, "main.txt").createNewFile();
      new File(test, "test.txt").createNewFile();
    }
  }

  @Test
  public void testGeneratedSources() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    BuilderExecution.builderExecution(basedir, GeneratedSourcesBuilder.class) //
        .execute() //
        .assertCompileSourceRoots(new File(basedir, "main")) //
        .assertTestCompileSourceRoots(new File(basedir, "test")) //
        .assertOutputFiles(basedir, "main/main.txt", "test/test.txt");
  }

  @Test
  public void testGeneratedSources_incremental() throws Exception {
    // assert that sources roots are injected in the project model during incremental build

    File basedir = temp.newFolder().getCanonicalFile();
    File stateFile = temp.newFile();

    // initial build
    InternalBuilderExecution.builderExecution(basedir, GeneratedSourcesBuilder.class) //
        .withStateFile(stateFile) //
        .execute() //
        .assertCompileSourceRoots(new File(basedir, "main")) //
        .assertTestCompileSourceRoots(new File(basedir, "test")) //
        .assertOutputFiles(basedir, "main/main.txt", "test/test.txt");

    // incremental no-change build
    InternalBuilderExecution.builderExecution(basedir, GeneratedSourcesBuilder.class) //
        .withStateFile(stateFile) //
        .execute() //
        .assertCompileSourceRoots(new File(basedir, "main")) //
        .assertTestCompileSourceRoots(new File(basedir, "test")) //
        .assertOutputFiles(basedir, new String[0]); // nothing generated during this build

    // assert generated files are still there
    assertDirectoryContents(basedir, "main/", "main/main.txt", "test/", "test/test.txt");
  }

  static class CreateDirectoriesAndFilesBuilder {
    @OutputDirectory(defaultValue = "${project.build.outputDirectory}")
    private File outputDirectory;

    @Parameter(required = false)
    private String[] directories;

    @Parameter(required = false)
    private String[] files;

    @Builder(name = "temporary-directory")
    public void createTemporaryDirectoryAndFiles() throws Exception {
      if (directories != null) {
        for (String directory : directories) {
          Files.createDirectories(new File(outputDirectory, directory).toPath());
        }
      }
      if (files != null) {
        for (String file : files) {
          Path path = new File(outputDirectory, file).toPath();
          Files.createDirectories(path.getParent());
          Files.newOutputStream(path).close();
        }
      }
    }
  }

  @Test
  public void testCreateDirectoriesAndFiles() throws Exception {
    // Tests that output directories are only deleted if they are empty on subsequent builds
    File basedir = temp.newFolder().getCanonicalFile();
    File stateFile = temp.newFile();

    // Initial build that generates one file and one directory
    InternalBuilderExecution.builderExecution(basedir, CreateDirectoriesAndFilesBuilder.class) //
        .withStateFile(stateFile) //
        .withProperty("project.build.outputDirectory", basedir.toString() + "/target") //
        .withConfiguration("directories", ImmutableList.of("dir1")) //
        .withConfiguration("files", ImmutableList.of("dir2/file")) //
        .execute().assertOutputFiles(basedir, "target/dir2/file");
    assertThat(new File(basedir, "target").list()).containsExactlyInAnyOrder("dir1", "dir2");

    // old file is deleted
    InternalBuilderExecution.builderExecution(basedir, CreateDirectoriesAndFilesBuilder.class) //
        .withStateFile(stateFile) //
        .withProperty("project.build.outputDirectory", basedir.toString() + "/target") //
        .withConfiguration("directories", ImmutableList.of("dir1")) //
        .execute().assertOutputFiles(basedir, new String[0]);
    assertThat(new File(basedir, "target").list()).containsExactlyInAnyOrder("dir1");

    // old directory is deleted, new file is created
    InternalBuilderExecution.builderExecution(basedir, CreateDirectoriesAndFilesBuilder.class) //
        .withStateFile(stateFile) //
        .withProperty("project.build.outputDirectory", basedir.toString() + "/target") //
        .withConfiguration("files", ImmutableList.of("dir2/file")) //
        .execute().assertOutputFiles(basedir, "target/dir2/file");
    assertThat(new File(basedir, "target").list()).containsExactlyInAnyOrder("dir2");

    // everything is deleted
    InternalBuilderExecution.builderExecution(basedir, CreateDirectoriesAndFilesBuilder.class) //
        .withStateFile(stateFile) //
        .withProperty("project.build.outputDirectory", basedir.toString() + "/target") //
        .execute().assertOutputFiles(basedir, new String[0]);
    assertThat(new File(basedir, "target").list()).isEmpty();
  }

  @Test
  public void testDirectoryCleanup_unrelatedFilesPresent() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    File stateFile = temp.newFile();

    File unrelated = new File(basedir, "target/dir/unrelated");

    // initial build that generates a file in a directory
    InternalBuilderExecution.builderExecution(basedir, CreateDirectoriesAndFilesBuilder.class) //
        .withStateFile(stateFile) //
        .withProperty("project.build.outputDirectory", basedir.toString() + "/target") //
        .withConfiguration("files", ImmutableList.of("dir/file")) //
        .execute().assertOutputFiles(basedir, "target/dir/file");
    assertThat(new File(basedir, "target/dir").list()).containsExactlyInAnyOrder("file");

    // create unrelated file in the same directory
    new FileOutputStream(unrelated).close();

    // delete the output file, retain unrelated file
    InternalBuilderExecution.builderExecution(basedir, CreateDirectoriesAndFilesBuilder.class) //
        .withStateFile(stateFile) //
        .withProperty("project.build.outputDirectory", basedir.toString() + "/target") //
        .execute().assertOutputFiles(basedir, new String[0]);
    assertThat(new File(basedir, "target/dir").list()).containsExactlyInAnyOrder("unrelated");

    // delete unrelated file and rerun the builder, directory is not deleted
    // note that directory is no longer tracked as builder output
    unrelated.delete();
    InternalBuilderExecution.builderExecution(basedir, CreateDirectoriesAndFilesBuilder.class) //
        .withStateFile(stateFile) //
        .withProperty("project.build.outputDirectory", basedir.toString() + "/target") //
        .execute().assertOutputFiles(basedir, new String[0]);
    assertThat(new File(basedir, "target").list()).containsExactlyInAnyOrder("dir");

    // trigger builder execution, directory still is not deleted (it's no longer tracked)
    InternalBuilderExecution.builderExecution(basedir, CreateDirectoriesAndFilesBuilder.class) //
        .withStateFile(stateFile) //
        .withProperty("project.build.outputDirectory", basedir.toString() + "/target") //
        .withConfiguration("files", ImmutableList.of("file")) //
        .execute().assertOutputFiles(basedir, "target/file");
    assertThat(new File(basedir, "target").list()).containsExactlyInAnyOrder("dir", "file");
  }

  //
  //
  //

  static class InvalidThreadedBuilder {

    @OutputFile
    File output;

    @Builder(name = "threade")
    public void execute() throws Exception {
      AtomicReference<Exception> exception = new AtomicReference<>();
      CountDownLatch latch = new CountDownLatch(1);

      // the worker thread attempts to write to an output file without PolicyContextPreserver
      // this is expected to fail because the thread does not have associated builder context

      new Thread() {
        @Override
        public void run() {
          try {
            Files.write(output.toPath(), "test".getBytes());
          } catch (Exception e) {
            exception.set(e);
          } finally {
            latch.countDown();
          }
        }
      }.start();
      latch.await();
      if (exception.get() != null) { throw exception.get(); }
    }
  }

  @Test
  public void testBuilderThreads_failure() throws Exception {
    // threads forked by builders should not have unrestricted resource access during tests

    // prime default filesystem instance to avoid, it is not relevant to the test
    FileSystems.getDefault();

    // InvalidThreadedBuilder does not correctly establish thread context for a worker thread
    // and is expected to fail
    thrown.expect(BuilderExecutionException.class);
    thrown.expectMessage("Cannot access system resources without builder context at this thread");

    File basedir = temp.newFolder().getCanonicalFile();
    InternalBuilderExecution.builderExecution(basedir, InvalidThreadedBuilder.class) //
        .withConfiguration("output", new File(basedir, "output").getCanonicalPath()) //
        .execute();
  }

  //
  //
  //

  static class ClasspathResourceReadingBuilder {
    static AtomicInteger COUNTER = new AtomicInteger();

    @Parameter
    String file;

    @Builder(name = "test")
    public void execute() {
      new File(file).lastModified();
      COUNTER.incrementAndGet();
    }
  }

  @Test
  public void testClassesDirectoryAccess() throws Exception {
    // the point of this test is to asset that builds can read files from classes directories

    File classes = temp.newFolder().getCanonicalFile();
    File file = new File(classes, "resource.txt");
    file.createNewFile();

    File basedir = temp.newFolder().getCanonicalFile();

    InternalBuilderExecution.builderExecution(basedir, ClasspathResourceReadingBuilder.class) //
        .withClasspath(Collections.singletonList(classes)) //
        .withParameterValue("file", file.getCanonicalPath()) //
        .execute();
    assertThat(ClasspathResourceReadingBuilder.COUNTER.get()).isEqualTo(1);
  }

  //
  //
  //

  static class CompileSourceRootsBuilder {
    @InputDirectory(value = "${project.compileSourceRoots}", includes = "**.*")
    private List<File> compileSourceRoots;

    @InputDirectory(value = "${project.testCompileSourceRoots}", includes = "**.*")
    private List<File> testCompileSourceRoots;

    @InputFile
    private File expectedMain;

    @InputFile
    private File expectedTest;

    @Builder(name = "generate")
    public void generate() throws IOException {
      assertThat(compileSourceRoots).containsExactly(expectedMain);
      assertThat(testCompileSourceRoots).containsExactly(expectedTest);
    }
  }

  @Test
  public void testCompileSourceRootParsing() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    File main = new File(basedir, "main");
    File test = new File(basedir, "test");

    BuilderExecution.builderExecution(basedir, CompileSourceRootsBuilder.class).withParameterValue("expectedMain", main)
        .withParameterValue("expectedTest", test).withCompileSourceRoot(main).withTestCompileSourceRoot(test).execute();
  }

  //
  //
  //

  static class SourceModeDependenciesBuilder {
    @Dependencies(scope = ResolutionScope.COMPILE)
    private List<File> dependencies;

    @Builder(name = "build")
    public void execute() throws Exception {
      dependencies.forEach(f -> walkit(f));
    }

    private void walkit(File file) {
      try {
        Files.walk(file.toPath()).filter(Files::isRegularFile).forEach(path -> {
          try {
            Files.readAllBytes(path);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Test
  public void testSourceModeDependenciesAllowedInList() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    File dep = temp.newFolder();
    File test = new File(dep, "test.txt");

    test.createNewFile();

    BuilderExecution.builderExecution(basedir, SourceModeDependenciesBuilder.class).withDependency("g:a:c", dep)
        .execute();
  }

  static class SourceModeDependencyMapBuilder {

    @Dependencies(scope = ResolutionScope.COMPILE)
    private Map<IArtifactMetadata, File> dependencyMap;

    @Builder(name = "build")
    public void execute() throws Exception {
      dependencyMap.values().forEach(f -> walkit(f));
    }

    private void walkit(File file) {
      try {
        Files.walk(file.toPath()).filter(Files::isRegularFile).forEach(path -> {
          try {
            Files.readAllBytes(path);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        });
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Test
  public void testSourceModeDependenciesAllowedInMap() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    File dep = temp.newFolder();
    File test = new File(dep, "test.txt");

    test.createNewFile();

    BuilderExecution.builderExecution(basedir, SourceModeDependencyMapBuilder.class).withDependency("g:a:c", dep)
        .execute();
  }

  //
  //
  //

  @SuppressWarnings("serial")
  static class CrashError extends Error {

  }

  static class CrashingBuilder {

    public static boolean CRASH = false;

    @OutputFile
    private File output;

    @Builder(name = "build")
    public void execute() throws IOException {
      if (output != null) {
        Files.newOutputStream(output.toPath()).close();
      }
      if (CRASH) { throw new CrashError(); }
    }
  }

  @Test
  public void testCrashingBuilder() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    File statedir = temp.newFolder().getCanonicalFile();
    File statefile = new File(statedir, "statefile");

    // simulated crash, assumes BuilderRunner does not catch Errors
    try {
      CrashingBuilder.CRASH = true;
      InternalBuilderExecution.builderExecution(basedir, CrashingBuilder.class) //
          .withStateFile(statefile) //
          .withConfiguration("output", "output") //
          .execute();
      fail(); // are not expected to get here
    } catch (CrashError expected) {
      // this is what this builder does, it crashes!
    }
    assertThat(new File(basedir, "output")).exists();
    assertThat(statefile).doesNotExist();

    // rerun after crash
    CrashingBuilder.CRASH = false;
    InternalBuilderExecution.builderExecution(basedir, CrashingBuilder.class) //
        .withStateFile(statefile) //
        .withConfiguration("output", "output") //
        .execute();
    assertThat(new File(basedir, "output")).exists();
    assertThat(statefile).exists();
  }

  //
  //
  //

  @SuppressWarnings("serial")
  static class CustomIOException extends IOException {
  }

  static class IncrementalBuilderExceptionBuilder {
    @Builder(name = "builder")
    public void execute() {
      throw new IncrementalBuildException(new CustomIOException());
    }
  }

  @Test
  public void testIncrementalBuildException() throws Exception {
    // assert that IncrementalBuildException are not handled as builder errors
    try {
      builderExecution(IncrementalBuilderExceptionBuilder.class).execute();
      fail();
    } catch (BuilderExecutionException e) {
      assertThat(e.getCause()).isInstanceOf(CustomIOException.class);
    }
  }

  //
  //
  //

  static class CanWriteBuilder {
    public static boolean CRASH = false;

    @OutputFile
    private File output;

    @Builder(name = "builder")
    public void execute() throws IOException {
      output.canWrite();
      if (CRASH) {
        // simulates crash after output file access was checked and recorded
        // but before the file was created on filesystem
        throw new CrashError();
      }
      new FileOutputStream(output).close();
    }
  }

  @Test
  public void testCrashBeforeWritingOutputFile() throws Exception {
    // assert recovery when crash occurs after file write was checked and recorded
    // but before the file was written.

    File basedir = temp.newFolder();
    File statefile = temp.newFile();

    try {
      CanWriteBuilder.CRASH = true;
      InternalBuilderExecution.builderExecution(basedir, CanWriteBuilder.class) //
          .withStateFile(statefile) //
          .withConfiguration("output", "output") //
          .execute();
      fail(); // are not expected to get here
    } catch (CrashError expected) {
      // this is what this builder does, it crashes!
    }
    assertThat(new File(basedir, "output")).doesNotExist(); // sanity check

    CanWriteBuilder.CRASH = false;
    InternalBuilderExecution.builderExecution(basedir, CanWriteBuilder.class) //
        .withStateFile(statefile) //
        .withConfiguration("output", "output") //
        .execute();
    assertThat(new File(basedir, "output")).exists();
  }
}
