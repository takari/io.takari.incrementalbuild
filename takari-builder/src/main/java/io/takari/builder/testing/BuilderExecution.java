package io.takari.builder.testing;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.IArtifactResources;
import io.takari.builder.enforcer.internal.EnforcerConfig;
import io.takari.builder.internal.BuilderContext;
import io.takari.builder.internal.BuilderInputs;
import io.takari.builder.internal.BuilderRunner;
import io.takari.builder.internal.ClasspathMatcher;
import io.takari.builder.internal.DependencyResolver;
import io.takari.builder.internal.JvmClasspathEntriesSupplier;
import io.takari.builder.internal.Reflection;
import io.takari.builder.internal.ResourceRoot;
import io.takari.builder.internal.digest.ClasspathDigester;
import io.takari.builder.internal.model.BuilderMethod;
import io.takari.builder.internal.workspace.FilesystemWorkspace;
import io.takari.incrementalbuild.workspace.Workspace;

public class BuilderExecution {

  private final Logger log = LoggerFactory.getLogger(getClass());

  static class TestValue implements BuilderInputs.Value<Object> {

    private final Object value;

    public TestValue(Object value) {
      this.value = value;
    }

    @Override
    public Object value() throws ReflectiveOperationException {
      return value;
    }
  }

  private final File projectBasedir;
  private final Class<?> builderType;
  private final String goal;
  private final Map<String, String> properties = new LinkedHashMap<>();
  private final Xpp3Dom configuration = new Xpp3Dom("configuration");
  private final Map<IArtifactMetadata, Path> dependencies = new LinkedHashMap<>();
  private final Map<String, BuilderInputs.Value<?>> forcedParameters = new LinkedHashMap<>();
  private final ClasspathMatcher classpathMatcher =
      new ClasspathMatcher(Arrays.asList(new JvmClasspathEntriesSupplier()));

  private final DependencyResolver dependencyResolver = new DependencyResolver() {

    @Override
    public Map<IArtifactMetadata, Path> getProjectDependencies(boolean transitive) {
      return Collections.unmodifiableMap(dependencies);
    }

    @Override
    public Map.Entry<IArtifactMetadata, Path> getProjectDependency(String groupId,
        String artifactId, String classifier) {
      for (Map.Entry<IArtifactMetadata, Path> entry : dependencies.entrySet()) {
        IArtifactMetadata key = entry.getKey();
        if (eq(groupId, key.getGroupId()) && eq(artifactId, key.getArtifactId())
            && eq(classifier, key.getClassifier())) {
          return entry;
        }
      }
      return null;
    }

    private boolean eq(String a, String b) {
      return a != null ? a.equals(b) : b == null;
    }
  };

  private EnforcerConfig enforcerConfig = EnforcerConfig.empty();
  private Workspace workspace = new FilesystemWorkspace();

  private File stateFile;

  final List<ResourceRoot> projectResources = new ArrayList<ResourceRoot>();

  final List<String> compileSourceRoots = new ArrayList<>();

  final List<String> testCompileSourceRoots = new ArrayList<>();

  List<File> classpath = Collections.emptyList();

  public BuilderExecution(File projectBasedir, Class<?> builderType, String goal) {
    this.projectBasedir = projectBasedir;
    this.builderType = builderType;
    this.goal = goal;
  }

  public static BuilderExecution builderExecution(File projectBasedir, Class<?> builder,
      String goal) {
    return new BuilderExecution(projectBasedir, builder, goal);
  }

  public static BuilderExecution builderExecution(File projectBasedir, Class<?> builder) {
    List<BuilderMethod> builders = Reflection.createBuilderClass(builder).builders();
    Assert.assertEquals("Ambiguous builder method", 1, builders.size());
    return builderExecution(projectBasedir, builder, builders.get(0).annotation().name());
  }

  public BuilderExecution withProperty(String name, String value) {
    properties.put(name, value);

    return this;
  }

  public BuilderExecution withConfiguration(String name, String value) {
    configuration.addChild(createXpp3Dom(name, value));
    return this;
  }

  public BuilderExecution withCompileSourceRoot(File compileSourceRoot) {
    this.compileSourceRoots.add(compileSourceRoot.getAbsolutePath());

    return this;
  }

  public BuilderExecution withTestCompileSourceRoot(File compileSourceRoot) {
    this.testCompileSourceRoots.add(compileSourceRoot.getAbsolutePath());

    return this;
  }

  /**
   * @noreference this method is provided to test {@link BuilderRunner}, it is not useful for
   *              testing of builder implementations.
   */
  BuilderExecution withStateFile(File stateFile) {
    this.stateFile = stateFile;

    return this;
  }

  /**
   * @noreference this method is provided to test {@link BuilderRunner}, it is not useful for
   *              testing of builder implementations.
   */
  BuilderExecution withClasspath(List<File> classpath) {
    this.classpath = new ArrayList<>(classpath);

    return this;
  }

  public BuilderExecution withConfiguration(String name, Collection<String> values) {
    Xpp3Dom childConfiguration = new Xpp3Dom(name);

    values.stream().forEach(value -> childConfiguration.addChild(createXpp3Dom("value", value)));
    configuration.addChild(childConfiguration);
    return this;
  }

  public BuilderExecutionResult execute() throws BuilderExecutionException {
    BuilderRuntime.enterTestScope();
    try {
      return executeWithoutRuntime();
    } finally {
      BuilderRuntime.leaveTestScope();
    }
  }

  BuilderExecutionResult executeWithoutRuntime() throws BuilderExecutionException {
    BuilderContext context = BuilderRunner.create(log, builderType, goal) //
        .setProjectBasedir(projectBasedir != null ? projectBasedir.toPath() : null) //
        .setStateFile(stateFile != null ? stateFile.toPath() : null) //
        .setProjectProperties(p -> properties.get(p)) //
        .setSessionClasspathMatcher(classpathMatcher.getMatcher()) //
        .setConfiguration(configuration) //
        .setDefaultMessageLocation(projectBasedir != null ? projectBasedir.toPath() : null, -1, -1) //
        .setProjectResourcesConsumer(resourceRoot -> this.projectResources.add(resourceRoot)) //
        .setProjectCompileSourceRoots(compileSourceRoots) //
        .setProjectTestCompileSourceRoots(testCompileSourceRoots) //
        .setDependencyResolver(dependencyResolver) //
        .setForcedParameters(forcedParameters) //
        .setBuilderEnforcerConfig(enforcerConfig) //
        .setClasspath(classpath.stream().map(f -> f.toPath()).collect(Collectors.toList()),
            new ClasspathDigester()) //
        .setBuilderId(goal) //
        .setWorkspace(workspace) //
        .execute(BuilderExecutionException::new);
    return new BuilderExecutionResult(this, context);
  }

  private Xpp3Dom createXpp3Dom(String name, String value) {
    Xpp3Dom dom = new Xpp3Dom(name);
    dom.setValue(value);
    return dom;
  }

  public BuilderExecution withConfigurationXml(String name, String xml)
      throws XmlPullParserException, IOException {
    configuration.addChild(
        Xpp3DomBuilder.build(new StringReader(String.format("<%s>%s</%s>", name, xml, name))));

    return this;
  }

  public BuilderExecution withInputDirectory(String name, File location)
      throws XmlPullParserException, IOException {
    StringReader xml = new StringReader(
        String.format("<%s><location>%s</location></%s>", name, location.getCanonicalPath(), name));
    configuration.addChild(Xpp3DomBuilder.build(xml));

    return this;
  }

  public BuilderExecution withInputDirectory(String name, File location,
      Collection<String> includes) throws XmlPullParserException, IOException {
    StringBuilder xml = new StringBuilder();
    xml.append("<").append(name).append(">");
    xml.append("<location>").append(location.getCanonicalPath()).append("</location>");
    xml.append("<includes>");
    includes.forEach(i -> xml.append("<include>").append(i).append("</include>"));
    xml.append("</includes>");
    xml.append("</").append(name).append(">");
    configuration.addChild(Xpp3DomBuilder.build(new StringReader(xml.toString())));

    return this;
  }

  public BuilderExecution withParameterValue(String parameter, Object value) {
    this.forcedParameters.put(parameter, new TestValue(value));

    return this;
  }

  public BuilderExecution withDependency(String coords, File content) {
    dependencies.put(newArtifactMetadata(coords), content.toPath());

    return this;
  }

  public BuilderExecution withEnforcerConfig(EnforcerConfig enforcerConfig) {
    this.enforcerConfig = enforcerConfig;

    return this;
  }

  /**
   * @noreference this method is provided to test {@link BuilderRunner}, it is not useful for
   *              testing of builder implementations.
   */
  BuilderExecution withWorkspace(Workspace workspace) {
    this.workspace = workspace;

    return this;
  }

  //
  //
  //

  /**
   * @param coords The artifact coordinates in the format
   *        {@code <groupId>:<artifactId>:[[<type>]:<classifier>]:<version>}, must not be
   *        {@code null}.
   */
  public static IArtifactMetadata newArtifactMetadata(String coords) {
    String[] parts = coords.split(":");
    String groupId = parts[0];
    String artifactId = parts[1];
    String type;
    String classifier;
    String version;
    if (parts.length == 3) {
      type = "jar";
      classifier = null;
      version = parts[2];
    } else if (parts.length == 4) {
      type = "jar";
      classifier = parts[2];
      version = parts[3];
    } else if (parts.length == 5) {
      type = parts[2];
      classifier = parts[3];
      version = parts[4];
    } else {
      throw new IllegalArgumentException();
    }

    return new IArtifactMetadata() {

      @Override
      public String getVersion() {
        return version;
      }

      @Override
      public String getType() {
        return type;
      }

      @Override
      public String getGroupId() {
        return groupId;
      }

      @Override
      public String getClassifier() {
        return classifier;
      }

      @Override
      public String getArtifactId() {
        return artifactId;
      }
    };
  }

  public static IArtifactResources newArtifactResources(String coords, URL... resources) {
    IArtifactMetadata artifact = newArtifactMetadata(coords);
    Set<URL> urls = new LinkedHashSet<>(Arrays.asList(resources));
    return new IArtifactResources() {

      @Override
      public IArtifactMetadata artifact() {
        return artifact;
      }

      @Override
      public Set<URL> resources() {
        return urls;
      }

    };
  }
}
