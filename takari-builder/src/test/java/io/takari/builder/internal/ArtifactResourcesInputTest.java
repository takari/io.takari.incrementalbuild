package io.takari.builder.internal;

import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.ArtifactResources;
import io.takari.builder.ResolutionScope;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidConfigurationException;
import io.takari.builder.internal.utils.JarBuilder;

public class ArtifactResourcesInputTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  File newJarArtifact(String... entries) throws IOException {
    return JarBuilder.create(temp.newFile()).withEntries(entries).build();
  }

  //
  //
  //

  static class _ListArtifactResourcesData {
    @ArtifactResources(resourcesRequired = true, scope = ResolutionScope.COMPILE)
    List<URL> resources;
  }

  @Test
  public void testArtifactResource() throws Exception {
    // fail when required @ArtifactResources does not have matching resources

    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(".resources: is required");

    File dependency = newJarArtifact("test.xml");

    TestInputBuilder builder = builder() //
        .withDependency("g:a", dependency);

    builder.build(_ListArtifactResourcesData.class, "resources");
  }

  @Test
  public void testArtifactResourceWithConfiguration() throws Exception {
    File dependency = newJarArtifact("test.xml");

    TestInputBuilder builder = builder() //
        .withConfigurationXml("<resources>" //
            + "<artifact><groupId>g</groupId><artifactId>a</artifactId></artifact>" //
            + "<include>test.xml</include>" + "</resources>") //
        .withDependency("g:a", dependency);

    @SuppressWarnings("unchecked")
    List<URL> value =
        (List<URL>) builder.build(_ListArtifactResourcesData.class, "resources").value();
    assertThat(value).hasSize(1);
    assertThat(value.get(0).getPath()).isEqualTo("test.xml");
  }

  static class _ListNonRequiredArtifactResourcesData {
    @ArtifactResources(scope = ResolutionScope.COMPILE)
    List<URL> resources;
  }

  @Test
  public void testNonRequiredArtifactResource() throws Exception {
    File dependency = newJarArtifact("test.xml");

    TestInputBuilder builder = builder() //
        .withDependency("g:a", dependency);

    assertThat(builder.build(_ListNonRequiredArtifactResourcesData.class, "resources") == null);
  }

  @Test
  public void testNonRequiredArtifactResourceWithConfiguration() throws Exception {
    File dependency = newJarArtifact("test.xml");

    TestInputBuilder builder = builder() //
        .withConfigurationXml("<resources>" //
            + "<artifact><groupId>g</groupId><artifactId>a</artifactId></artifact>" //
            + "<include>test.xml</include>" + "</resources>") //
        .withDependency("g:a", dependency);

    @SuppressWarnings("unchecked")
    List<URL> value =
        (List<URL>) builder.build(_ListNonRequiredArtifactResourcesData.class, "resources").value();
    assertThat(value).hasSize(1);
    assertThat(value.get(0).getPath()).isEqualTo("test.xml");
  }
}
