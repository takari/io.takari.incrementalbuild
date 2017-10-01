package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderInputs.digest;
import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.DependencyResources;
import io.takari.builder.IArtifactMetadata;
import io.takari.builder.IArtifactResources;
import io.takari.builder.ResolutionScope;
import io.takari.builder.internal.BuilderInputs.ArtifactResourcesValue;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidConfigurationException;
import io.takari.builder.internal.TestInputBuilder.TestArtifactMetadata;
import io.takari.builder.internal.utils.JarBuilder;

public class DependencyResourcesInputTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  File newDirArtifact(String... entries) throws IOException {
    File dir = temp.newFolder().getCanonicalFile();
    for (String entry : entries) {
      Files.createFile(dir.toPath().resolve(entry));
    }
    return dir;
  }

  File newJarArtifact(String... entries) throws IOException {
    return JarBuilder.create(temp.newFile().getCanonicalFile()).withEntries(entries).build();
  }

  URL newResource(String name, String content) throws IOException {
    File file = temp.newFile().getCanonicalFile();
    Files.write(file.toPath(), content.getBytes());
    TestArtifactMetadata artifact = new TestArtifactMetadata("g:a");
    return ArtifactResourceURLStreamHandler.newURL(artifact, name, file.toURI().toURL());
  }

  static ArtifactResourcesValue newArtifactResourcesValue(String artifactId, URL... resources) {
    IArtifactMetadata artifact = new TestArtifactMetadata("g:" + artifactId);
    return new ArtifactResourcesValue(Collections.emptySet(), artifact, Arrays.asList(resources));
  }

  @Test
  public void testArtifactResourcesDigest() throws Exception {
    URL a = newResource("a", "a");
    URL a_copy = newResource("acopy", "a");
    URL a_diff = newResource("a", "a diff");
    URL b = newResource("b", "b");

    ArtifactResourcesValue value = newArtifactResourcesValue("artifactA", a, b);

    assertThat(digest(value)) //
        .isEqualTo(digest(newArtifactResourcesValue("artifactA", a, b)));

    assertThat(digest(value)) //
        .as("same bytes, different resources names") //
        .isNotEqualTo(digest(newArtifactResourcesValue("artifactA", a_copy, b)));

    assertThat(digest(value)) //
        .as("same resources, different artifacts") //
        .isNotEqualTo(digest(newArtifactResourcesValue("artifactB", a, b)));

    assertThat(digest(value)) //
        .as("same resources names, different bytes") //
        .isNotEqualTo(digest(newArtifactResourcesValue("artifactA", a_diff, b)));
  }

  //
  //
  //

  static class _ListURLData {
    @DependencyResources(resourcesRequired = true, scope = ResolutionScope.COMPILE)
    List<URL> resources;
  }

  @Test
  public void testSpecificDependency() throws Exception {
    File dependency = newJarArtifact("test.xml");

    TestInputBuilder builder = builder() //
        .withConfigurationXml("<resources>" //
            + "<dependency><groupId>g</groupId><artifactId>a</artifactId></dependency>" //
            + "<include>test.xml</include>" + "</resources>") //
        .withDependency("g:a", dependency);

    @SuppressWarnings("unchecked")
    List<URL> value = (List<URL>) builder.build(_ListURLData.class, "resources").value();
    assertThat(value).hasSize(1);
    assertThat(value.get(0).getPath()).isEqualTo("test.xml");
  }

  @Test
  public void testAllDependency() throws Exception {
    File dependency = newJarArtifact("test.xml");

    TestInputBuilder builder = builder() //
        .withConfigurationXml("<resources>" //
            + "<include>test.xml</include>" + "</resources>") //
        .withDependency("g:a", dependency);

    @SuppressWarnings("unchecked")
    List<URL> value = (List<URL>) builder.build(_ListURLData.class, "resources").value();
    assertThat(value).hasSize(1);
    assertThat(value.get(0).getPath()).isEqualTo("test.xml");
  }

  @Test
  public void testSubdirDependency() throws Exception {
    File dependency = newJarArtifact("dir/test.xml");

    TestInputBuilder builder = builder() //
        .withConfigurationXml("<resources>" //
            + "<include>dir/test.xml</include>" + "</resources>") //
        .withDependency("g:a", dependency);

    @SuppressWarnings("unchecked")
    List<URL> value = (List<URL>) builder.build(_ListURLData.class, "resources").value();
    assertThat(value).hasSize(1);
    assertThat(value.get(0).getPath()).isEqualTo("dir/test.xml");
  }

  @Test
  public void testDependencyWithOpenEndedMatcher() throws Exception {
    File dependency = newJarArtifact("dir/test.xml");

    TestInputBuilder builder = builder() //
        .withConfigurationXml("<resources>" //
            + "<include>dir/**/test.xml</include>" + "</resources>") //
        .withDependency("g:a", dependency);

    @SuppressWarnings("unchecked")
    List<URL> value = (List<URL>) builder.build(_ListURLData.class, "resources").value();
    assertThat(value).hasSize(1);
    assertThat(value.get(0).getPath()).isEqualTo("dir/test.xml");
  }

  @Test
  public void testDependencies() throws Exception {
    File dependency = newJarArtifact("a.txt");

    TestInputBuilder builder = builder() //
        .withConfigurationXml("<resources>" //
            + "<dependencies>" //
            + "<dependency><groupId>g</groupId><artifactId>a</artifactId></dependency>" //
            + "</dependencies>" //
            + "<includes><include>a.txt</include></includes>" + "</resources>") //
        .withDependency("g:a", dependency);

    @SuppressWarnings("unchecked")
    List<URL> value = (List<URL>) builder.build(_ListURLData.class, "resources").value();
    assertThat(value).hasSize(1);
    assertThat(value.get(0).getPath()).isEqualTo("a.txt");
  }

  @Test
  public void testDependencyResources_inputFiles_jar() throws Exception {
    File dependency = newJarArtifact("a.txt");
    BuilderInputs inputs = builder() //
        .withConfigurationXml("<resources><include>**/*</include></resources>") //
        .withDependency("g:a", dependency) //
        .build(_ListURLData.class);
    assertThat(inputs.inputFiles).contains(dependency.toPath());
  }

  @Test
  public void testDependencyResources_inputFiles_dir() throws Exception {
    File dependency = newDirArtifact("a.txt");
    BuilderInputs inputs = builder() //
        .withConfigurationXml("<resources><include>**/*</include></resources>") //
        .withDependency("g:a", dependency) //
        .build(_ListURLData.class);
    assertThat(inputs.inputFiles).contains(new File(dependency, "a.txt").toPath());
  }

  @Test
  public void testMissingDependency() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("dependency g:a does not exist");

    builder() //
        .withConfigurationXml("<resources>" //
            + "<dependency><groupId>g</groupId><artifactId>a</artifactId></dependency>" //
            + "<include>file.txt</include>" //
            + "</resources>") //
        .build(_ListURLData.class, "resources");
  }

  @Test
  public void testMissingRequiredIncludes() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("_ListURLData.resources: <includes> is required");

    File dependency = newJarArtifact();

    builder() //
        .withConfigurationXml("<resources>" //
            + "<dependency><groupId>g</groupId><artifactId>a</artifactId></dependency>" //
            + "</resources>") //
        .withDependency("g:a", dependency).build(_ListURLData.class, "resources");
  }

  @Test
  public void testEmptyDependencies() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("_ListURLData.resources: is required");

    builder() //
        .withConfigurationXml("<resources>" //
            + "<dependencies/>" //
            + "<includes><include>a.txt</include></includes>" + "</resources>") //
        .build(_ListURLData.class, "resources");
  }

  @Test
  public void testDependenciesAndDependency() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage("Use <dependencies> or <dependency>");

    builder() //
        .withConfigurationXml("<resources>" //
            + "<dependencies>" //
            + "<dependency><groupId>g</groupId><artifactId>a</artifactId></dependency>" //
            + "</dependencies>" //
            + "<dependency><groupId>g</groupId><artifactId>a1</artifactId></dependency>" //
            + "<includes><include>a.txt</include></includes>" + "</resources>") //
        .build(_ListURLData.class, "resources");
  }

  @Test
  public void testDependencyURLPath_dir() throws Exception {
    File dependency = newDirArtifact("a.txt");
    @SuppressWarnings("unchecked")
    List<URL> resources = (List<URL>) builder() //
        .withConfigurationXml("<resources><include>**/*</include></resources>") //
        .withDependency("g:a", dependency) //
        .build(_ListURLData.class, "resources").value();
    assertThat(resources).extracting(u -> u.getPath()).containsExactly("a.txt");
  }

  @Test
  public void testDependencyURLPath_jar() throws Exception {
    File dependency = newJarArtifact("a.txt");
    @SuppressWarnings("unchecked")
    List<URL> resources = (List<URL>) builder() //
        .withConfigurationXml("<resources><include>**/*</include></resources>") //
        .withDependency("g:a", dependency) //
        .build(_ListURLData.class, "resources").value();
    assertThat(resources).extracting(u -> u.getPath()).containsExactly("a.txt");
  }

  @Test
  public void testListURLOrder() throws Exception {
    File jarA = newJarArtifact("a.txt");
    File jarB = newJarArtifact("a.txt");

    @SuppressWarnings("unchecked")
    List<URL> ab = (List<URL>) builder() //
        .withConfigurationXml("<resources><include>**/*</include></resources>") //
        .withDependency("g:a", jarA) //
        .withDependency("g:b", jarB) //
        .build(_ListURLData.class, "resources").value();
    assertThat(ab).extracting(u -> u.getHost()).containsExactly("[g:a:null]", "[g:b:null]");

    @SuppressWarnings("unchecked")
    List<URL> ba = (List<URL>) builder() //
        .withConfigurationXml("<resources><include>**/*</include></resources>") //
        .withDependency("g:b", jarB) //
        .withDependency("g:a", jarA) //
        .build(_ListURLData.class, "resources").value();
    assertThat(ba).extracting(u -> u.getHost()).containsExactly("[g:b:null]", "[g:a:null]");
  }

  //
  //
  //

  static class _ListArtifactResourcesData {
    @DependencyResources(resourcesRequired = true, scope = ResolutionScope.COMPILE)
    List<IArtifactResources> resources;
  }

  @Test
  public void testCollectionDependencyResources() throws Exception {
    File dependencyA = newJarArtifact("a.txt");
    File dependencyB = newJarArtifact("b.txt");

    TestInputBuilder builder = builder() //
        .withConfigurationXml("<resources>" + "<resources>"
            + "<dependency><groupId>g</groupId><artifactId>a</artifactId></dependency>"
            + "<includes><include>**/a.txt</include></includes>" + "</resources>" + "<resources>"
            + "<dependency>g:b</dependency>" + "<include>**/b.txt</include>" + "</resources>"
            + "</resources>")
        .withDependency("g:a", dependencyA).withDependency("g:b", dependencyB);

    @SuppressWarnings("unchecked")
    List<IArtifactResources> list = (List<IArtifactResources>) builder //
        .build(_ListArtifactResourcesData.class, "resources").value();

    assertThat(list).hasSize(2);
    assertThat(list.get(0).resources().iterator().next().getPath()).isEqualTo("a.txt");
    assertThat(list.get(1).resources().iterator().next().getPath()).isEqualTo("b.txt");
  }

  @Test
  public void testDependencyWithoutMatchingResources() throws Exception {
    File dependencyA = newJarArtifact("a.txt");
    File dependencyB = newJarArtifact("b.txt");

    TestInputBuilder builder = builder() //
        .withConfigurationXml("<resources>" + "<dependency>g:a</dependency>"
            + "<dependency>g:b</dependency>" + "<include>a.txt</include>" + "</resources>")
        .withDependency("g:a", dependencyA).withDependency("g:b", dependencyB);

    @SuppressWarnings("unchecked")
    List<IArtifactResources> list = (List<IArtifactResources>) builder //
        .build(_ListArtifactResourcesData.class, "resources").value();

    // g:b does not have matching resources and must not be included

    assertThat(list).hasSize(1) //
        .extracting(r -> r.artifact().getArtifactId()).element(0).isEqualTo("a");
  }

  @Test
  public void testNoMatchingDependencies() throws Exception {
    // fail when required @ArtifactResources does not have matching resources

    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(".resources: is required");

    File dependencyA = newJarArtifact("a.txt");
    File dependencyB = newJarArtifact("b.txt");

    builder() //
        .withConfigurationXml(
            "<resources>" + "<dependency>g:a</dependency>" + "<dependency>g:b</dependency>"
                + "<include>no-such-resource</include>" + "</resources>")
        .withDependency("g:a", dependencyA).withDependency("g:b", dependencyB)
        .build(_ListArtifactResourcesData.class, "resources");
  }

  //
  //
  //

  static class _DataWithIncludesExcludes {
    @DependencyResources(resourcesRequired = true, includes = "**/*.txt", excludes = "**/broken.txt", scope = ResolutionScope.COMPILE)
    List<URL> resources;
  }

  @Test
  public void testResourceNameIncludesExcludes() throws Exception {
    File dependency = newJarArtifact("test.txt", "broken.txt");

    @SuppressWarnings("unchecked")
    List<URL> urls = (List<URL>) builder() //
        .withDependencies(dependency.getAbsolutePath())
        .build(_DataWithIncludesExcludes.class, "resources") //
        .value();

    assertThat(urls).hasSize(1);
  }

  @Test
  public void testIncludesExcludesConfigurationNotAllowed() throws Exception {
    thrown.expect(InvalidConfigurationException.class);

    builder().withConfigurationXml("<resources>" //
        + "<includes><include>a.txt</include></includes>" + "</resources>") //
        .build(_DataWithIncludesExcludes.class, "resources");
  }

  //
  //
  //

  static class _ArtifactResourcesData {
    @DependencyResources(resourcesRequired = true, scope = ResolutionScope.COMPILE, includes = "**/*.txt")
    IArtifactResources resources;
  }

  @Test
  public void testArtifactResources() throws Exception {
    File dependencyA = newJarArtifact("a.txt");

    IArtifactResources resources = (IArtifactResources) builder() //
        .withDependency("g:a", dependencyA) //
        .build(_ArtifactResourcesData.class, "resources") //
        .value();

    assertThat(resources.artifact().getArtifactId()).isEqualTo("a");
    assertThat(resources.resources()).hasSize(1);
  }

  @Test
  public void testArtifactResources_noMatchingResources() throws Exception {
    // fail when required @ArtifactResources does not have matching resources

    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(".resources: is required");

    File dependencyA = newJarArtifact("a.not-txt");

    builder() //
        .withDependency("g:a", dependencyA) //
        .build(_ArtifactResourcesData.class, "resources");
  }

  //
  //
  //

  static class _ArrayResourceData {
    @DependencyResources(includes = "**/*.txt", scope = ResolutionScope.COMPILE)
    URL[] resources;
  }

  @Test
  public void testArrayResources() throws Exception {
    File dependency = newJarArtifact("a.txt");

    URL[] urls = (URL[]) builder() //
        .withDependencies(dependency.getAbsolutePath()).build(_ArrayResourceData.class, "resources") //
        .value();

    assertThat(urls).hasSize(1);
  }
}
