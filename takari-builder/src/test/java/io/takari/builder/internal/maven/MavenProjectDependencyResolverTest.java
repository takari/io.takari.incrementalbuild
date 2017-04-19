package io.takari.builder.internal.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.IArtifactMetadata;
import io.takari.maven.testing.TestMavenRuntime;

public class MavenProjectDependencyResolverTest {

  private static final String FILE_DEP_ID = "my.file";
  private static final String JAR_DEP_ID = "my.jar";
  private static final String TXT_FILE_NAME = "test.txt";

  @Rule
  public final TestMavenRuntime maven = new TestMavenRuntime();

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private MavenProject project;
  private File resourceDir;
  private File resourceJar;

  @Before
  public void setup() throws Exception {
    project = createProject();
    resourceDir = createFileDependency();
    resourceJar = createJarDependency();
  }

  private MavenProject createProject() throws IOException, Exception {
    File basedir = temp.newFolder();
    File file = new File(basedir, "1.txt");

    file.createNewFile();

    return maven.readMavenProject(basedir);
  }

  private File createFileDependency() throws IOException {
    File filedir = temp.newFolder();
    File file = new File(filedir, TXT_FILE_NAME);

    file.createNewFile();

    return filedir;
  }

  private File createNonMatchingFileDependency() throws IOException {
    File filedir = temp.newFolder();
    File file = new File(filedir, "thisnotfound.txt");

    file.createNewFile();

    return filedir;
  }

  private File createJarDependency() throws IOException {
    File jar = temp.newFile("test.jar");

    FileOutputStream fos = new FileOutputStream(jar);
    JarOutputStream out = new JarOutputStream(fos);

    JarEntry jarEntry = new JarEntry(TXT_FILE_NAME);
    out.putNextEntry(jarEntry);

    out.close();

    return jar;
  }

  @Test(expected = NoSuchElementException.class)
  public void testResourceNotFound() throws Exception {
    createNonMatchingFileDependency();
    MavenProjectDependencyResolver resolver = new MavenProjectDependencyResolver(project);
    resolver.getProjectDependency("g", "a", "c");
  }

  @Test
  public void testResolveAllDependencies() throws Exception {
    addMavenDependenciesToProject();
    MavenProjectDependencyResolver resolver = new MavenProjectDependencyResolver(project);
    Map<IArtifactMetadata, Path> resolved = resolver.getProjectDependencies(true);
    assertThat(resolved).containsValue(resourceDir.toPath());
    assertThat(resolved).containsValue(resourceJar.toPath());
  }

  @Test
  public void testResolveDirectDependencies() throws Exception {
    maven.newDependency(resourceDir).setArtifactId(FILE_DEP_ID).addTo(project, true);
    maven.newDependency(resourceJar).setArtifactId(JAR_DEP_ID).addTo(project, false);
    MavenProjectDependencyResolver resolver = new MavenProjectDependencyResolver(project);
    Map<IArtifactMetadata, Path> resolved = resolver.getProjectDependencies(false);
    assertThat(resolved).containsValue(resourceDir.toPath());
    assertThat(resolved).doesNotContainValue(resourceJar.toPath());
  }

  private void addMavenDependenciesToProject() throws Exception {
    maven.newDependency(resourceDir).setArtifactId(FILE_DEP_ID).addTo(project);
    maven.newDependency(resourceJar).setArtifactId(JAR_DEP_ID).addTo(project);
  }
}
