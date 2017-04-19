package io.takari.builder.internal;

import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.GeneratedResourcesDirectory;
import io.takari.builder.internal.BuilderInputs.GeneratedResourcesDirectoryValue;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidConfigurationException;

public class GeneratedResourcesDirectoryTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  class _DefaultTestData {
    @GeneratedResourcesDirectory(defaultValue = "default", defaultIncludes = "**/*.default.include",
        defaultExcludes = "**/*.default.exclude")
    File resources;
  }

  class _TestData {
    @GeneratedResourcesDirectory(value = "value", includes = "**/*.include", excludes = "**/*.exclude")
    File resources;
  }

  class _DefaultPathTestData {
    @GeneratedResourcesDirectory(defaultValue = "default", defaultIncludes = "**/*.default.include",
        defaultExcludes = "**/*.default.exclude")
    Path resources;
  }

  @Test
  public void testConfigurationOverrides() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    GeneratedResourcesDirectoryValue value = builder(basedir) //
        .withConfigurationXml("<resources>config</resources>") //
        .build(_DefaultTestData.class, "resources");

    assertValue(value, new File(basedir, "config"), "[**/*.default.include]", "[**/*.default.exclude]");

    value = builder(basedir) //
        .withConfigurationXml("<resources>" + "<location>config</location>" + "</resources>") //
        .build(_DefaultTestData.class, "resources");

    assertValue(value, new File(basedir, "config"), "[**/*.default.include]", "[**/*.default.exclude]");

    value = builder(basedir) //
        .withConfigurationXml("<resources>" + "<location>config</location>"
            + "<includes><include>**/*.config.include</include></includes>"
            + "<excludes><exclude>**/*.config.exclude</exclude></excludes>" + "</resources>") //
        .build(_DefaultTestData.class, "resources");

    assertValue(value, new File(basedir, "config"), "[**/*.config.include]", "[**/*.config.exclude]");
  }


  @Test
  public void testInvalidResourcesConfiguration() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    GeneratedResourcesDirectoryValue value = builder(basedir) //
        .withConfigurationXml("<resources>config</resources>") //
        .build(_TestData.class, "resources");

    // TODO - should this throw an exception as well?
    assertValue(value, new File(basedir, "value"), "[**/*.include]", "[**/*.exclude]");
  }

  @Test
  public void testInvalidLocationConfiguration() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    GeneratedResourcesDirectoryValue value = builder(basedir) //
        .withConfigurationXml("<resources>" + "<location>config</location>" + "</resources>") //
        .build(_TestData.class, "resources");

    // TODO - should this throw an exception as well?
    assertValue(value, new File(basedir, "value"), "[**/*.include]", "[**/*.exclude]");
  }

  @Test(expected = InvalidConfigurationException.class)
  public void testInvalidIncludesExcludesConfiguration() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    builder(basedir) //
        .withConfigurationXml("<resources>" + "<includes><include>**/*.config.include</include></includes>"
            + "<excludes><exclude>**/*.config.exclude</exclude></excludes>" + "</resources>") //
        .build(_TestData.class, "resources");
  }

  @Test
  public void testResourceRoots() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    BuilderInputs inputs = builder(basedir)
        .withConfigurationXml("<resources>" + "<location>config</location>"
            + "<includes><include>**/*.config.include</include></includes>"
            + "<excludes><exclude>**/*.config.exclude</exclude></excludes>" + "</resources>") //
        .build(_DefaultTestData.class);

    List<ResourceRoot> roots = new ArrayList<ResourceRoot>(inputs.getResourceRoots());
    assertEquals(1, roots.size());
    assertEquals(new File(basedir, "config").getCanonicalPath(), roots.get(0).getLocation());
    assertEquals("[**/*.config.include]", roots.get(0).getIncludes().toString());
    assertEquals("[**/*.config.exclude]", roots.get(0).getExcludes().toString());
  }

  @Test
  public void testPathTarget() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();

    GeneratedResourcesDirectoryValue value = builder(basedir) //
        .withConfigurationXml("<resources>config</resources>") //
        .build(_DefaultPathTestData.class, "resources");

    assertEquals(Paths.get(basedir.getCanonicalPath(), "config"), value.value());
  }

  private void assertValue(GeneratedResourcesDirectoryValue value, File file, String includes, String excludes) {
    assertEquals(file, value.value());
    assertEquals(includes, value.includes.toString());
    assertEquals(excludes, value.excludes.toString());
  }
}
