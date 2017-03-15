package io.takari.builder.internal;

import static io.takari.builder.internal.TestInputBuilder.builder;
import static io.takari.maven.testing.TestResources.touch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.Builder;
import io.takari.builder.Dependencies;
import io.takari.builder.InputFile;
import io.takari.builder.NonDeterministic;
import io.takari.builder.Parameter;
import io.takari.builder.ResolutionScope;
import io.takari.builder.internal.BuilderInputs.Digest;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidConfigurationException;

public class BuilderInputsTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  static class _Data {
    @Parameter("a")
    String string;

    @InputFile("a")
    File file;
  }

  @Test
  public void testDigest() throws Exception {
    File basedir = temp.newFolder();
    File fileA = new File(basedir, "a");
    fileA.createNewFile();

    TestInputBuilder builder = builder(basedir);

    Digest digest = builder.build(_Data.class).getDigest();

    touch(fileA);

    assertNotEquals(digest, builder.build(_Data.class).getDigest());
  }

  @Test
  public void testNoParameterDigest() throws Exception {
    Digest digest = builder().build(Object.class).getDigest();
    Digest emtydigest =
        BuilderExecutionState.load(new File(temp.getRoot(), "nosuchfile").toPath()).inputsDigest;
    assertFalse(digest.equals(emtydigest));
  }

  @Test
  public void testBacktrace() throws Exception {
    thrown.expect(InvalidConfigurationException.class);
    thrown.expectMessage(CoreMatchers.startsWith("_Data.string:"));

    builder().withConfigurationXml("<string>b</string>").build(_Data.class, "string");
  }

  static class _Defaults {
    @Parameter(defaultValue = "${defaultProperty}", required = false)
    String defaultString;
  }

  @Test
  public void testDefinedPropertyDefaultValue() throws Exception {
    // defined property should properly set value
    final String propertyValue = "test";
    assertEquals(propertyValue, builder().withProperty("defaultProperty", propertyValue)
        .build(_Defaults.class, "defaultString").value());
  }

  @Test
  public void testUndefinedPropertyDefaultValue() throws Exception {
    // undefined property as a Parameter's defaultValue should be set to null
    assertEquals(null, builder().build(_Defaults.class, "defaultString"));
  }

  static class _Values {
    @Parameter(value = "${defaultProperty}", required = false)
    String string;
  }

  @Test
  public void testDefinedPropertyValue() throws Exception {
    // defined property should properly set value
    final String propertyValue = "test";
    assertEquals(propertyValue, builder().withProperty("defaultProperty", propertyValue)
        .build(_Values.class, "string").value());
  }

  @Test(expected = InvalidConfigurationException.class)
  public void testUndefinedPropertyValue() throws Exception {
    // undefined property as a Parameter's value should fail the build
    builder().build(_Values.class);
  }

  //
  // Non public builder @NonDeterministic method is discovered
  //

  static class _nonPublicBuilder {
    @NonDeterministic
    @Builder(name = "generate")
    void execute() {}
  }

  @Test
  public void testNonPublicBuilder() throws Exception {
    TestInputBuilder builder = builder();

    BuilderInputs inputs = builder.withGoal("generate").build(_nonPublicBuilder.class);

    assertTrue(inputs.isNonDeterministic());
  }

  //
  //
  //

  static class _Dependency {
    @Dependencies(scope = ResolutionScope.COMPILE)
    List<File> dependencies;
  }

  @Test
  public void testUnresolvedDependency() throws Exception {
    File repodir = temp.newFolder().getCanonicalFile();
    File dependency = new File(repodir, "nosuchfile");
    BuilderInputs inputs = builder() //
        .withDependency("g:a", dependency) //
        .build(_Dependency.class);
    assertNotNull(inputs.getDigest());
    assertThat(inputs.getInputFiles()).isEmpty();
  }
}
