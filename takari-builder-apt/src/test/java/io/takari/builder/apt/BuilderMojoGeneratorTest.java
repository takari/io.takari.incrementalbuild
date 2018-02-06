package io.takari.builder.apt;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

import java.util.Arrays;

import javax.annotation.processing.AbstractProcessor;
import javax.tools.JavaFileObject;

import org.junit.Test;

import com.google.testing.compile.JavaFileObjects;

public class BuilderMojoGeneratorTest {

  @Test
  public void testClassCompiles() throws Exception {

    JavaFileObject definitionClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;", "", "import io.takari.builder.*;", "", "  public class TestClass {", "",
        "  @Parameter", "  private String myString;", "", "  @Builder(name=\"test-builder\")",
        "  public void execute() {}", "", "}");

    assertAbout(javaSources()).that(Arrays.asList(definitionClass))
        .processedWith(newMojoGenerator()).compilesWithoutError();

  }

  private AbstractProcessor newMojoGenerator() {
    return new BuilderMojoGenerator();
  }

  @Test
  public void testInterfaceDoesNotCompile() throws Exception {

    JavaFileObject definitionClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;", "", "import io.takari.builder.*;", "", "  public interface TestClass {",
        "", "  @Builder(name=\"test-builder\")", "  public void execute(Messages messages);", "",
        "}");

    assertAbout(javaSources()).that(Arrays.asList(definitionClass))
        .processedWith(newMojoGenerator()).failsToCompile()
        .withErrorContaining("Only concrete classes are allowed to contain Builder methods");

  }

  @Test
  public void testAbstractClassDoesNotCompile() throws Exception {
    JavaFileObject definitionClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;", "", "import io.takari.builder.*;", "",
        "  public abstract class TestClass {", "", "  @Builder(name=\"test-builder\")",
        "  public abstract void execute(Messages messages);", "", "}");

    assertAbout(javaSources()).that(Arrays.asList(definitionClass))
        .processedWith(newMojoGenerator()).failsToCompile()
        .withErrorContaining("Only concrete classes are allowed to contain Builder methods");
  }

  @Test
  public void testClassImplementingInterfaceDoesNotCompile() throws Exception {

    JavaFileObject definitionClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;", "", "import io.takari.builder.*;", "",
        "  public class TestClass implements java.io.Serializable {", "",
        "  @Builder(name=\"test-builder\")", "  public void execute() {}", "", "}");

    assertAbout(javaSources()).that(Arrays.asList(definitionClass))
        .processedWith(newMojoGenerator()).compilesWithoutError();
  }

  @Test
  public void testUnrelatedClass() throws Exception {

    JavaFileObject unrelatedClass = JavaFileObjects.forSourceLines("test.UnrelatedClass",
        "package test;", "public class UnrelatedClass implements java.io.Serializable {}");

    JavaFileObject builderClass = JavaFileObjects.forSourceLines("test.BuilderClass",
        "package test;", "import io.takari.builder.*;", "public class BuilderClass {",
        "  @Builder(name=\"test-builder\") public void execute() {}", "}");

    assertAbout(javaSources()).that(Arrays.asList(unrelatedClass, builderClass))
        .processedWith(newMojoGenerator()).compilesWithoutError();
  }

  @Test
  public void testOnlyTopLevelParamsAggregated() throws Exception {

    JavaFileObject definitionClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;", "", "import io.takari.builder.*;", "", "import java.io.File;", "",
        "  public class TestClass {", "", "  @Parameter", "  private Inner myInner;", "",
        "  @Builder(name=\"test-builder\")", "  public void execute() {}", "",
        "  private static class Inner {", "    @InputFile File innerFile;", "  }", "", "", "}");

    JavaFileObject generatedClass =
        JavaFileObjects.forSourceLines("test.TestClass$GeneratedMojo$test_builder", "package test;",
            "" + "import io.takari.builder.internal.maven.AbstractIncrementalMojo;",
            "import org.apache.maven.plugins.annotations.LifecyclePhase;",
            "import org.apache.maven.plugins.annotations.Mojo;",
            "import org.apache.maven.plugins.annotations.Parameter;",
            "import org.apache.maven.plugins.annotations.ResolutionScope;",
            "import org.codehaus.plexus.configuration.PlexusConfiguration;", "", "@Mojo(",
            "    name = \"test-builder\",", "    defaultPhase = LifecyclePhase.NONE,",
            "    requiresProject = true,", "requiresDependencyResolution = ResolutionScope.NONE,",
            "    threadSafe = true", ")",
            "public class TestClass$GeneratedMojo$test_builder extends AbstractIncrementalMojo {",
            "", "  @Parameter", "  PlexusConfiguration myInner;", "",
            "  public TestClass$GeneratedMojo$test_builder() {", "    super(TestClass.class);",
            "  }", "}");

    assertAbout(javaSources()).that(Arrays.asList(definitionClass))
        .processedWith(newMojoGenerator()).compilesWithoutError().and()
        .generatesSources(generatedClass);

  }

  @Test
  public void testDeprecatedAnnotation() throws Exception {

    JavaFileObject definitionClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;", "", "import io.takari.builder.*;", "", "import java.lang.Deprecated;", "",
        "import java.io.File;", "", "public class TestClass {", "", "  @Parameter", "  @Deprecated",
        "  private String foo;", "", "  @Builder(name=\"test-builder\")",
        "  public void execute() {}", "", "}");

    JavaFileObject generatedClass =
        JavaFileObjects.forSourceLines("test.TestClass$GeneratedMojo$test_builder", "package test;",
            "" + "import io.takari.builder.internal.maven.AbstractIncrementalMojo;",
            "import java.lang.Deprecated;",
            "import org.apache.maven.plugins.annotations.LifecyclePhase;",
            "import org.apache.maven.plugins.annotations.Mojo;",
            "import org.apache.maven.plugins.annotations.Parameter;",
            "import org.apache.maven.plugins.annotations.ResolutionScope;",
            "import org.codehaus.plexus.configuration.PlexusConfiguration;", "", "@Mojo(",
            "    name = \"test-builder\",", "    defaultPhase = LifecyclePhase.NONE,",
            "    requiresProject = true,", "requiresDependencyResolution = ResolutionScope.NONE,",
            "    threadSafe = true", ")",
            "public class TestClass$GeneratedMojo$test_builder extends AbstractIncrementalMojo {",
            "", "  @Parameter", "  @Deprecated", "  PlexusConfiguration foo;", "",
            "  public TestClass$GeneratedMojo$test_builder() {", "    super(TestClass.class);",
            "  }", "}");

    assertAbout(javaSources()).that(Arrays.asList(definitionClass))
        .processedWith(newMojoGenerator()).compilesWithoutError().and()
        .generatesSources(generatedClass);

  }

  @Test
  public void testExtendsClass() throws Exception {

    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass", "package test;", "",
        "import io.takari.builder.*;", "", "  public class TestClass extends AbstractClass {", "",
        "   @Builder(name=\"test-builder\")", "   @Override", "   public void execute() {}", "",
        "}");

    JavaFileObject abstractClass =
        JavaFileObjects.forSourceLines("test.AbstractClass", "package test;", "",
            "import io.takari.builder.*;", "", "public abstract class AbstractClass {", "",
            "  @Parameter String parameter;", "", "  protected abstract void execute();", "}");

    JavaFileObject generatedClass =
        JavaFileObjects.forSourceLines("test.TestClass$GeneratedMojo$test_builder", "package test;",
            "" + "import io.takari.builder.internal.maven.AbstractIncrementalMojo;",
            "import org.apache.maven.plugins.annotations.LifecyclePhase;",
            "import org.apache.maven.plugins.annotations.Mojo;",
            "import org.apache.maven.plugins.annotations.Parameter;",
            "import org.apache.maven.plugins.annotations.ResolutionScope;",
            "import org.codehaus.plexus.configuration.PlexusConfiguration;", "", "@Mojo(",
            "    name = \"test-builder\",", "    defaultPhase = LifecyclePhase.NONE,",
            "    requiresProject = true,", "requiresDependencyResolution = ResolutionScope.NONE,",
            "    threadSafe = true", ")",
            "public class TestClass$GeneratedMojo$test_builder extends AbstractIncrementalMojo {",
            "", "  @Parameter", "  PlexusConfiguration parameter;", "",
            "  public TestClass$GeneratedMojo$test_builder() {", "    super(TestClass.class);",
            "  }", "}");

    assertAbout(javaSources()).that(Arrays.asList(abstractClass, testClass))
        .processedWith(newMojoGenerator()).compilesWithoutError().and()
        .generatesSources(generatedClass);
  }

  @Test
  public void testDuplicateInheritedParameterName() throws Exception {

    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass", "package test;", "",
        "import io.takari.builder.*;", "", "  public class TestClass extends AbstractClass {", "",
        "  private @Parameter String parameter;", "", "   @Builder(name=\"test-builder\")",
        "   @Override", "   public void execute(Messages messages) {", "", "   }", "", "}");

    JavaFileObject abstractClass = JavaFileObjects.forSourceLines("test.AbstractClass",
        "package test;", "", "import io.takari.builder.*;", "",
        "public abstract class AbstractClass {", "", "  private @Parameter String parameter;", "",
        "  protected abstract void execute(Messages messages);", "}");

    assertAbout(javaSources()).that(Arrays.asList(abstractClass, testClass))
        .processedWith(newMojoGenerator()).failsToCompile().withErrorContaining(
            "Builder parameter 'parameter' duplicates parameter defined in test.AbstractClass");
  }

  @Test
  public void testAllParameterAnnotations() throws Exception {
    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass", "package test;", "",
        "import java.io.File;", "import java.net.URL;", "import java.util.List;",
        "import io.takari.builder.*;", "", "public class TestClass {", "",
        "  @Parameter String parameter;", "",
        "  @InputDirectory(includes=\"**/*\") File inputDirectory;", "",
        "  @InputDirectoryFiles IDirectoryFiles inputDirectoryFiles;", "",
        "  @InputFile File inputFile;", "", "  @OutputDirectory File outputDirectory;", "",
        "  @OutputFile File outputFile;", "",
        "  @GeneratedSourcesDirectory File generatedSourcesDirectory;", "",
        "  @DependencyResources(scope=ResolutionScope.COMPILE) List<URL> dependencyResources;", "",
        "  @Dependencies(scope=ResolutionScope.COMPILE) List<File> dependencies;", "",
        "  @Builder(name=\"test-builder\")", "  public void execute() {}", "", "}");

    JavaFileObject generatedClass =
        JavaFileObjects.forSourceLines("test.TestClass$GeneratedMojo$test_builder", "package test;",
            "", "import io.takari.builder.internal.maven.AbstractIncrementalMojo;",
            "import org.apache.maven.plugins.annotations.LifecyclePhase;",
            "import org.apache.maven.plugins.annotations.Mojo;",
            "import org.apache.maven.plugins.annotations.Parameter;",
            "import org.apache.maven.plugins.annotations.ResolutionScope;",
            "import org.codehaus.plexus.configuration.PlexusConfiguration;", "", "@Mojo(",
            "    name = \"test-builder\",", "    defaultPhase = LifecyclePhase.NONE,",
            "    requiresProject = true,", "requiresDependencyResolution = ResolutionScope.NONE,",
            "    threadSafe = true", ")",
            "public class TestClass$GeneratedMojo$test_builder extends AbstractIncrementalMojo {",
            "  @Parameter", "  PlexusConfiguration parameter;", "", "  @Parameter",
            "  PlexusConfiguration inputDirectory;", "", "  @Parameter",
            "  PlexusConfiguration inputDirectoryFiles;", "", "  @Parameter",
            "  PlexusConfiguration inputFile;", "", "  @Parameter",
            "  PlexusConfiguration outputDirectory;", "", "  @Parameter",
            "  PlexusConfiguration outputFile;", "", "  @Parameter",
            "  PlexusConfiguration generatedSourcesDirectory;", "", "  @Parameter",
            "  PlexusConfiguration dependencyResources;", "", "  @Parameter",
            "  PlexusConfiguration dependencies;", "",
            "  public TestClass$GeneratedMojo$test_builder() {", "    super(TestClass.class);",
            "  }", "}");

    assertAbout(javaSources()).that(Arrays.asList(testClass)).processedWith(newMojoGenerator())
        .compilesWithoutError().and().generatesSources(generatedClass);
  }

  @Test
  public void testPrimitiveParameter() throws Exception {
    JavaFileObject validClass = JavaFileObjects.forSourceLines("test.TestClass", "package test;",
        "import io.takari.builder.*;", "public class TestClass {", "  @Parameter int param;",
        "  @Builder(name=\"test-builder\") public void execute() {}", "}");
    assertAbout(javaSources()).that(Arrays.asList(validClass)) //
        .processedWith(newMojoGenerator()) //
        .compilesWithoutError();

    JavaFileObject invalidClass = JavaFileObjects.forSourceLines("test.TestClass", "package test;",
        "import io.takari.builder.*;", "public class TestClass {",
        "  @Parameter(required=false) int param;",
        "  @Builder(name=\"test-builder\") public void execute() {}", "}");
    assertAbout(javaSources()).that(Arrays.asList(invalidClass)) //
        .processedWith(newMojoGenerator()) //
        .failsToCompile()
        .withErrorContaining("Parameter 'param' of primitive type 'int' must be required");
  }

  @Test
  public void testResolutionScopeWithMultiValue() throws Exception {
    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass", "package test;", "",
        "import java.io.File;", "import java.net.URL;", "import java.util.List;",
        "import io.takari.builder.*;", "", "public class TestClass {",
        "  @DependencyResources(scope=ResolutionScope.COMPILE) List<IArtifactResources> dependencyResources;",
        "", "  @Builder(name=\"test-builder\")", "  public void execute() {}", "", "}");

    JavaFileObject generatedClass =
        JavaFileObjects.forSourceLines("test.TestClass$GeneratedMojo$test_builder", "package test;",
            "", "import io.takari.builder.internal.maven.AbstractIncrementalMojo;",
            "import org.apache.maven.plugins.annotations.LifecyclePhase;",
            "import org.apache.maven.plugins.annotations.Mojo;",
            "import org.apache.maven.plugins.annotations.Parameter;",
            "import org.apache.maven.plugins.annotations.ResolutionScope;",
            "import org.codehaus.plexus.configuration.PlexusConfiguration;", "", "@Mojo(",
            "    name = \"test-builder\",", "    defaultPhase = LifecyclePhase.NONE,",
            "    requiresProject = true,", "requiresDependencyResolution = ResolutionScope.NONE,",
            "    threadSafe = true", ")",
            "public class TestClass$GeneratedMojo$test_builder extends AbstractIncrementalMojo {",
            "  @Parameter", "  PlexusConfiguration dependencyResources;", "",
            "  public TestClass$GeneratedMojo$test_builder() {", "    super(TestClass.class);",
            "  }", "}");

    assertAbout(javaSources()).that(Arrays.asList(testClass)).processedWith(newMojoGenerator())
        .compilesWithoutError().and().generatesSources(generatedClass);
  }

  @Test
  public void testResolutionScopeWithComposite() throws Exception {
    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass", "package test;", "",
        "import java.io.File;", "import java.net.URL;", "import io.takari.builder.*;", "",
        "public class TestClass {", "  @Parameter ExampleComposite compositeWithResolutionScope;",
        "", "  @Builder(name=\"test-builder\")", "  public void execute() {}", "",
        "  public static class ExampleComposite {",
        "    @DependencyResources(scope=ResolutionScope.COMPILE) IArtifactResources turtles;",
        "  }", "}");

    JavaFileObject generatedClass =
        JavaFileObjects.forSourceLines("test.TestClass$GeneratedMojo$test_builder", "package test;",
            "", "import io.takari.builder.internal.maven.AbstractIncrementalMojo;",
            "import org.apache.maven.plugins.annotations.LifecyclePhase;",
            "import org.apache.maven.plugins.annotations.Mojo;",
            "import org.apache.maven.plugins.annotations.Parameter;",
            "import org.apache.maven.plugins.annotations.ResolutionScope;",
            "import org.codehaus.plexus.configuration.PlexusConfiguration;", "", "@Mojo(",
            "    name = \"test-builder\",", "    defaultPhase = LifecyclePhase.NONE,",
            "    requiresProject = true,", "requiresDependencyResolution = ResolutionScope.NONE,",
            "    threadSafe = true", ")",
            "public class TestClass$GeneratedMojo$test_builder extends AbstractIncrementalMojo {",
            "  @Parameter", "  PlexusConfiguration compositeWithResolutionScope;", "",
            "  public TestClass$GeneratedMojo$test_builder() {", "    super(TestClass.class);",
            "  }", "}");

    assertAbout(javaSources()).that(Arrays.asList(testClass)).processedWith(newMojoGenerator())
        .compilesWithoutError().and().generatesSources(generatedClass);
  }
}
