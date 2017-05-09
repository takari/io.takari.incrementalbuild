package io.takari.builder.internal;

import static io.takari.builder.internal.Reflection.getAllFields;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Function;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.internal.BuilderInputs.Value;
import io.takari.builder.internal.model.AbstractParameter;
import io.takari.builder.internal.model.BuilderClass;
import io.takari.builder.internal.workspace.FilesystemWorkspace;

class TestInputBuilder {

  public static class TestArtifactMetadata implements IArtifactMetadata {

    private final String groupId;

    private final String artifactId;

    public TestArtifactMetadata(String gac) {
      StringTokenizer st = new StringTokenizer(gac, ":");
      groupId = st.nextToken();
      artifactId = st.nextToken();
    }

    @Override
    public String getGroupId() {
      return groupId;
    }

    @Override
    public String getArtifactId() {
      return artifactId;
    }

    @Override
    public String getVersion() {
      return null;
    }

    @Override
    public String getType() {
      return null;
    }

    @Override
    public String getClassifier() {
      return null;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
      result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      TestArtifactMetadata other = (TestArtifactMetadata) obj;
      if (artifactId == null) {
        if (other.artifactId != null) return false;
      } else if (!artifactId.equals(other.artifactId)) return false;
      if (groupId == null) {
        if (other.groupId != null) return false;
      } else if (!groupId.equals(other.groupId)) return false;
      return true;
    }

  }

  class TestDependencyResolver implements DependencyResolver {
    @Override
    public Map<IArtifactMetadata, Path> getProjectDependencies(boolean transitive) {
      Map<IArtifactMetadata, Path> result = new LinkedHashMap<>();
      dependencies.forEach((gac, path) -> {
        result.put(new TestArtifactMetadata(gac), path);
      });
      return result;
    }

    @Override
    public SimpleEntry<IArtifactMetadata, Path> getProjectDependency(String groupId,
        String artifactId, String classifier) {
      String gac = groupId + ":" + artifactId;
      if (classifier != null) {
        gac = gac + ":" + classifier;
      }
      Path path = dependencies.get(gac);
      return path != null ? new SimpleEntry<>(new TestArtifactMetadata(gac), path) : null;
    }
  }

  class TestPropertyResolver implements Function<String, String> {
    @Override
    public String apply(String name) {
      if (properties.containsKey(name)) {
        return properties.get(name);
      }
      return null;
    }
  }

  private File basedir;
  private final DependencyResolver dependencyResolver = new TestDependencyResolver();
  private final ExpressionEvaluator expressionEvaluator =
      new ExpressionEvaluator(Arrays.asList(new TestPropertyResolver()));
  private final List<String> compileSourceRoots = new ArrayList<>();
  private final List<String> testCompileSourceRoots = new ArrayList<>();
  private final ProjectModelProvider projectModelProvider = new ProjectModelProvider() {

    @Override
    public List<String> getTestCompileSourceRoots() {
      return testCompileSourceRoots;
    }

    @Override
    public List<String> getCompileSourceRoots() {
      return compileSourceRoots;
    }

    @Override
    public Path getBasedir() {
      return basedir.toPath();
    }
  };

  private Xpp3Dom configuration;
  private final Map<String, Path> dependencies = new LinkedHashMap<>();
  private final Map<String, String> properties = new LinkedHashMap<>();
  private String goal = "goal";
  private final BuilderWorkspace workspace;

  public TestInputBuilder(File basedir) {
    this.basedir = basedir != null ? basedir : new File("/");
    this.workspace = new BuilderWorkspace(new FilesystemWorkspace(), this.basedir.toPath(), null);
  }

  @SuppressWarnings("unchecked")
  public <T extends Value<?>> T build(Class<?> type, String fieldName) throws IOException {
    BuilderClass metadata = Reflection.createBuilderClass(type);
    AbstractParameter parameter = metadata.parameters().stream() //
        .filter(p -> fieldName.equals(p.name())) //
        .findAny().get();
    metadata = new BuilderClass(metadata.type(), metadata.builders(),
        Collections.singletonList(parameter));
    BuilderInputs inputs =
        BuilderInputsBuilder.build(goal, projectModelProvider, dependencyResolver,
            expressionEvaluator, type, configuration, Collections.emptyMap(), metadata, workspace);
    return (T) inputs.members.get(getField(type, fieldName));
  }

  public BuilderInputs build(Class<?> type) throws IOException {
    return BuilderInputsBuilder.build(goal, projectModelProvider, dependencyResolver,
        expressionEvaluator, type, configuration, Collections.emptyMap(), workspace);
  }

  static Field getField(Class<?> type, String name) {
    return getAllFields(type).stream() //
        .filter(f -> name.equals(f.getName())) //
        .findFirst().get();
  }

  public static TestInputBuilder builder(File basedir) {
    return new TestInputBuilder(basedir);
  }

  public static TestInputBuilder builder() {
    return new TestInputBuilder(null);
  }

  public TestInputBuilder withBasedir(File basedir) {
    this.basedir = basedir;

    return this;
  }

  public TestInputBuilder withConfiguration(Xpp3Dom configuration) {
    this.configuration = configuration;

    return this;
  }

  public TestInputBuilder withCompileSourceRoot(String sourceRoot) {
    this.compileSourceRoots.add(sourceRoot);

    return this;
  }

  public TestInputBuilder withTestCompileSourceRoot(String sourceRoot) {
    this.testCompileSourceRoots.add(sourceRoot);

    return this;
  }

  public TestInputBuilder withConfigurationXml(String xml)
      throws XmlPullParserException, IOException {

    this.configuration =
        Xpp3DomBuilder.build(new StringReader("<configuration>" + xml + "</configuration>"));

    return this;
  }

  public TestInputBuilder withDependencies(String... dependencies) {
    for (String dependency : dependencies) {
      Path path = Paths.get(dependency);
      this.dependencies.put("g:" + path.getFileName().toString(), path);
    }
    return this;
  }

  public TestInputBuilder withDependency(String gac, File dependency) {
    this.dependencies.put(gac, dependency.toPath());
    return this;
  }

  public TestInputBuilder withProperty(String key, String value) {
    this.properties.put(key, value);
    return this;
  }

  public TestInputBuilder withGoal(String goal) {
    this.goal = goal;
    return this;
  }
}
