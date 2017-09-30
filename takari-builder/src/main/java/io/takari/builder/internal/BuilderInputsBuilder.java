package io.takari.builder.internal;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.codehaus.plexus.util.xml.Xpp3Dom;

import io.takari.builder.GeneratedSourcesDirectory;
import io.takari.builder.IArtifactMetadata;
import io.takari.builder.internal.BuilderInputs.ArtifactResourcesValue;
import io.takari.builder.internal.BuilderInputs.CollectionValue;
import io.takari.builder.internal.BuilderInputs.CompositeValue;
import io.takari.builder.internal.BuilderInputs.DependencyMapValue;
import io.takari.builder.internal.BuilderInputs.DependencyValue;
import io.takari.builder.internal.BuilderInputs.GeneratedResourcesDirectoryValue;
import io.takari.builder.internal.BuilderInputs.GeneratedSourcesDirectoryValue;
import io.takari.builder.internal.BuilderInputs.InputDirectoryValue;
import io.takari.builder.internal.BuilderInputs.InputFileValue;
import io.takari.builder.internal.BuilderInputs.InputFilesValue;
import io.takari.builder.internal.BuilderInputs.InstanceFactory;
import io.takari.builder.internal.BuilderInputs.ListArtifactResourcesValue;
import io.takari.builder.internal.BuilderInputs.MapValue;
import io.takari.builder.internal.BuilderInputs.OutputDirectoryValue;
import io.takari.builder.internal.BuilderInputs.OutputFileValue;
import io.takari.builder.internal.BuilderInputs.StringValue;
import io.takari.builder.internal.BuilderInputs.Value;
import io.takari.builder.internal.Reflection.ReflectionField;
import io.takari.builder.internal.Reflection.ReflectionType;
import io.takari.builder.internal.cache.JarEntriesCache;
import io.takari.builder.internal.model.AbstractFileParameter;
import io.takari.builder.internal.model.AbstractParameter;
import io.takari.builder.internal.model.AbstractResourceSelectionParameter;
import io.takari.builder.internal.model.ArtifactResourcesParameter;
import io.takari.builder.internal.model.BuilderClass;
import io.takari.builder.internal.model.BuilderMetadataVisitor;
import io.takari.builder.internal.model.BuilderMethod;
import io.takari.builder.internal.model.BuilderValidationVisitor;
import io.takari.builder.internal.model.CompositeParameter;
import io.takari.builder.internal.model.DependenciesParameter;
import io.takari.builder.internal.model.DependencyResourcesParameter;
import io.takari.builder.internal.model.GeneratedResourcesDirectoryParameter;
import io.takari.builder.internal.model.GeneratedSourcesDirectoryParameter;
import io.takari.builder.internal.model.InputDirectoryFilesParameter;
import io.takari.builder.internal.model.InputDirectoryParameter;
import io.takari.builder.internal.model.InputFileParameter;
import io.takari.builder.internal.model.MapParameter;
import io.takari.builder.internal.model.MultivalueParameter;
import io.takari.builder.internal.model.OutputDirectoryParameter;
import io.takari.builder.internal.model.OutputFileParameter;
import io.takari.builder.internal.model.SimpleParameter;
import io.takari.builder.internal.model.TypeAdapter;
import io.takari.builder.internal.model.UnsupportedCollectionParameter;
import io.takari.builder.internal.pathmatcher.FileMatcher;
import io.takari.builder.internal.pathmatcher.JarEntries;

public class BuilderInputsBuilder implements BuilderMetadataVisitor {

  static final String XML_CONFIG_LOCATION = "location";
  static final String XML_CONFIG_INCLUDES = "includes";
  static final String XML_CONFIG_INCLUDE = "include";
  static final String XML_CONFIG_EXCLUDES = "excludes";
  static final String XML_CONFIG_EXCLUDE = "exclude";

  static final String SOURCE_ROOTS_EXPR = "${project.compileSourceRoots}";
  static final String TEST_SOURCE_ROOTS_EXPR = "${project.testCompileSourceRoots}";

  private final ProjectModelProvider projectModelProvider;
  private final DependencyResolver dependencyResolver;
  private final ExpressionEvaluator expressionEvaluator;
  private final Xpp3Dom builderConfiguration;
  private final Map<String, Value<?>> forcedParameters;
  private final String goal;
  private final BuilderWorkspace workspace;
  private boolean isNonDeterministic;

  Context context;
  final Map<Field, Value<?>> parameters = new LinkedHashMap<>();

  BuilderInputsBuilder(String goal, ProjectModelProvider projectModelProvider,
      DependencyResolver dependencyResolver, ExpressionEvaluator expressionEvaluator,
      Xpp3Dom configuration, Map<String, Value<?>> forcedParameters, BuilderWorkspace workspace) {

    this.goal = goal;
    this.projectModelProvider = projectModelProvider;
    this.dependencyResolver = dependencyResolver;
    this.expressionEvaluator = expressionEvaluator;
    this.builderConfiguration = configuration;
    this.forcedParameters = forcedParameters;
    this.workspace = workspace;
  }

  public static BuilderInputs build(String goal, ProjectModelProvider projectModelProvider,
      DependencyResolver dependencyResolver, ExpressionEvaluator expressionEvaluator,
      Class<?> clazz, Xpp3Dom configuration, Map<String, Value<?>> forcedParameters,
      BuilderWorkspace builderWorkspace) throws IOException {

    BuilderClass metadata = Reflection.createBuilderClass(clazz);

    return build(goal, projectModelProvider, dependencyResolver, expressionEvaluator, clazz,
        configuration, forcedParameters, metadata, builderWorkspace);
  }

  static BuilderInputs build(String goal, ProjectModelProvider mavenModelProvider,
      DependencyResolver dependencyResolver, ExpressionEvaluator expressionEvaluator,
      Class<?> clazz, Xpp3Dom configuration, Map<String, Value<?>> forcedParameters,
      BuilderClass metadata, BuilderWorkspace workspace) {

    BuilderValidationVisitor vv = new BuilderValidationVisitor() {
      @Override
      protected void error(AbstractParameter parameter, String message) {
        throw new InvalidModelException();
      }

      @Override
      protected void error(BuilderMethod builder, String message) {
        throw new InvalidModelException();
      }
    };
    metadata.accept(vv);

    BuilderInputsBuilder v = new BuilderInputsBuilder(goal, mavenModelProvider, dependencyResolver,
        expressionEvaluator, configuration, forcedParameters, workspace);

    metadata.accept(v);

    return new BuilderInputs(clazz, v.parameters, v.isNonDeterministic);
  }

  //
  //
  //

  static boolean isEmpty(String str) {
    return str == null || str.trim().isEmpty();
  }

  @SuppressWarnings("serial")
  static class InvalidConfigurationException extends RuntimeException {

    public final Context context;

    public InvalidConfigurationException(Context context, String message) {
      super(context.getBacktrace() + ": " + message);
      this.context = context;
    }

    public InvalidConfigurationException(Context context, String message, Throwable cause) {
      super(context.getBacktrace() + ": " + message, cause);
      this.context = context;
    }
  }

  @SuppressWarnings("serial")
  static class InvalidModelException extends RuntimeException {

  }

  class Evaluator {
    private final Context context;

    private String[] value;

    private Xpp3Dom configuration;

    private String[] defaultValue;

    public Evaluator(Context context) {
      this.context = context;
    }

    public Evaluator withValue(String[] value) {
      assert this.value == null;
      this.value = value;

      return this;
    }

    public Evaluator withConfiguration(Xpp3Dom configuration) {
      assert this.configuration == null;
      this.configuration = configuration;

      return this;
    }

    public Evaluator withDefaultValue(String[] defaultValue) {
      assert this.defaultValue == null;
      this.defaultValue = defaultValue;

      return this;
    }

    private Stream<String> configuration() {
      if (configuration == null) {
        return Stream.empty();
      }
      // TODO assert both value and children are not present simultaneously
      Stream<Xpp3Dom> result;
      if (configuration.getChildCount() > 0) {
        result = Stream.of(configuration.getChildren());
      } else {
        result = Stream.of(configuration);
      }
      return result //
          .map(xml -> xml != null ? xml.getValue() : null) //
          .filter(str -> !isEmpty(str));
    }

    public Stream<String> asStrings() {
      return asStrings(false);
    }

    public Stream<String> asStrings(boolean allowSourceRoots) {
      if (value != null && value.length > 0) {
        if (configuration().count() > 0) {
          throw new InvalidConfigurationException(context, "configuration is not allowed");
        }

        String[] valueToEvaluate = parseSourceRoots(value, allowSourceRoots);

        return Stream.of(valueToEvaluate).map(s -> evaluate(s));
      }

      List<String> configuration = configuration().collect(Collectors.toList());
      if (!configuration.isEmpty()) {
        return configuration.stream();
      }

      if (defaultValue != null && defaultValue.length > 0) {
        String[] valueToEvaluate = parseSourceRoots(defaultValue, allowSourceRoots);
        return Stream.of(valueToEvaluate).map(s -> {
          try {
            return evaluate(s);
          } catch (InvalidConfigurationException e) {
            return null;
          }
        }).filter(s -> s != null);
      }

      return Stream.empty();
    }

    private String[] parseSourceRoots(String[] initialValue, boolean allowSourceRoots) {
      List<String> values = Arrays.asList(initialValue);

      String expr = null;
      Iterator<String> valIter = values.iterator();
      String prevVal = null;

      while (valIter.hasNext()) {
        String currentVal = valIter.next();
        if (expr != null) {
          throw new InvalidConfigurationException(context,
              String.format("%s can not have other values provided along with it", expr));
        }
        if (currentVal.equals(SOURCE_ROOTS_EXPR) || currentVal.equals(TEST_SOURCE_ROOTS_EXPR)) {
          if (prevVal != null) {
            throw new InvalidConfigurationException(context,
                String.format("%s can not have other values provided along with it", currentVal));
          }
          if (!allowSourceRoots) {
            throw new InvalidConfigurationException(context,
                String.format("%s expression is not allowed here", currentVal));
          }

          expr = currentVal;
        }
        prevVal = currentVal;
      }

      if (SOURCE_ROOTS_EXPR.equals(expr)) {
        List<String> sourceRoots = projectModelProvider.getCompileSourceRoots();
        return sourceRoots.toArray(new String[sourceRoots != null ? sourceRoots.size() : 0]);
      }

      if (TEST_SOURCE_ROOTS_EXPR.equals(expr)) {
        List<String> sourceRoots = projectModelProvider.getTestCompileSourceRoots();
        return sourceRoots.toArray(new String[sourceRoots != null ? sourceRoots.size() : 0]);
      }
      return initialValue;
    }

    public Stream<Path> asPaths() {
      return asStrings().map(s -> toPath(s));
    }

    public Stream<Path> asPathsWithSourceRoots() {
      return asStrings(true).map(s -> toPath(s));
    }

    public Path asPath() {
      // TODO validate value has no more than one element
      if (value != null && value.length > 0) {
        return toPath(evaluate(value[0]));
      }

      // TODO validate configuration does not have children
      if (configuration != null) {
        if (configuration.getValue().equals(SOURCE_ROOTS_EXPR)
            || configuration.getValue().equals(TEST_SOURCE_ROOTS_EXPR)) {
          throw new InvalidConfigurationException(context, String
              .format("%s expression is not allowed in configuration", configuration.getValue()));
        }
        return toPath(evaluate(configuration.getValue()));
      }

      // TODO validate defaultValue has no more than one element
      if (defaultValue != null && defaultValue.length > 0) {
        try {
          return toPath(evaluate(defaultValue[0]));
        } catch (InvalidConfigurationException e) {
          // could not evaluate, there is no default value
        }
      }

      return null;
    }

    private Path toPath(String path) {
      Path file = PathNormalizer.toPath(path);
      if (!file.isAbsolute()) {
        file = projectModelProvider.getBasedir().toAbsolutePath().resolve(path);
      }

      try {
        return file.toFile().getCanonicalFile().toPath();
      } catch (IOException e) {
        return file.normalize();
      }
    }

    private String evaluate(String str) {
      if (isEmpty(str)) {
        return null;
      }
      try {
        return expressionEvaluator.evaluate(str.trim());
      } catch (ExpressionEvaluationException e) {
        throw new InvalidConfigurationException(context, e.getMessage(), e);
      }
    }
  }

  abstract class Context {
    public final Context parent;

    public final String name;

    public final Xpp3Dom configuration;

    protected Context(Context parent, String name, Xpp3Dom configuration) {
      this.parent = parent;
      this.name = name;
      this.configuration = configuration;
    }

    public abstract void accept(Value<?> value);

    public abstract List<Value<?>> values();

    public abstract Value<?> value();

    public abstract Stream<Xpp3Dom> configuration();

    public MultivalueContext multivalueSubcontext(boolean required) {
      return new MultivalueContext(this, name, configuration, required);
    }

    public SingletonContext singletonSubcontext(String name, boolean required) {
      Xpp3Dom subConfiguration = configuration != null ? configuration.getChild(name) : null;
      return new SingletonContext(this, "." + name, subConfiguration, required);
    }

    public Evaluator evaluator() {
      return new Evaluator(this);
    }

    public String getBacktrace() {
      StringBuilder sb = new StringBuilder();
      for (Context ctx = this; ctx != null; ctx = ctx.parent) {
        sb.insert(0, ctx.name);
      }
      return sb.toString();
    }
  }

  class SingletonContext extends Context {

    private final boolean required;

    public SingletonContext(Context parent, String name, Xpp3Dom configuration, boolean required) {
      super(parent, name, configuration);
      this.required = required;
    }

    private Value<?> value;

    @Override
    public void accept(Value<?> value) {
      assert this.value == null; // TODO validate to make sure this is always the case
      this.value = value;
    }

    @Override
    public List<Value<?>> values() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Value<?> value() {
      if (required && value == null) {
        throw new InvalidConfigurationException(context, "is required");
      }
      return value;
    }

    @Override
    public Stream<Xpp3Dom> configuration() {
      return configuration != null ? Stream.of(configuration) : Stream.empty();
    }
  }

  class MultivalueContext extends Context {

    private final List<Value<?>> values = new ArrayList<>();
    private final boolean required;

    public MultivalueContext(Context parent, String name, Xpp3Dom configuration, boolean required) {
      super(parent, name, configuration);
      this.required = required;
    }

    @Override
    public void accept(Value<?> value) {
      this.values.add(value);
    }

    @Override
    public List<Value<?>> values() {
      if (values.isEmpty()) {
        if (required) {
          throw new InvalidConfigurationException(context, "is required");
        }
        return null;
      }
      return values;
    }

    @Override
    public Value<?> value() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Stream<Xpp3Dom> configuration() {
      return configuration != null ? Stream.of(configuration.getChildren()) : Stream.empty();
    }

    public SingletonContext elementSubcontext(Xpp3Dom configuration) {
      String name = "[" + values.size() + "]";
      return new SingletonContext(this, name, configuration, required);
    }
  }

  static class ResourceSelection<B> {
    final B bucket;

    final Path location;

    final List<String> includes;

    final List<String> excludes;

    public ResourceSelection(B bucket, Path location) {
      this(bucket, location, null, null);
    }

    public ResourceSelection(B bucket, Path location, List<String> includes,
        List<String> excludes) {
      this.bucket = bucket;
      this.location = location;
      this.includes = includes;
      this.excludes = excludes;
    }
  }

  abstract class ResourceSelector<V extends Value<?>, B> {
    final String xmlMultivalue;

    final String xmlLocationPlural;

    final String xmlLocationSingular;

    final AbstractResourceSelectionParameter parameter;

    protected ResourceSelector(String multivalue, String locationPlural, String locationSingular,
        AbstractResourceSelectionParameter parameter) {
      this.xmlMultivalue = multivalue;
      this.xmlLocationPlural = locationPlural;
      this.xmlLocationSingular = locationSingular;
      this.parameter = parameter;
    }

    abstract Map.Entry<B, Path> evaluateLocation(Xpp3Dom location) throws UncheckedIOException;

    abstract Map.Entry<B, Path> evaluateLocation(String location) throws UncheckedIOException;

    abstract V newBucketValue(ResourceSelection<B> selection, List<Path> paths);

    abstract Value<?> newCollectionResourceValue(TypeAdapter type,
        Map<ResourceSelection<B>, List<Path>> resources, TypeAdapter parameterType);

    protected boolean shouldAllowSourceRoots() {
      return false;
    }

    final void select() {
      List<ResourceSelection<B>> selections = null;

      if (parameter.value().length > 0) {
        selections = parseLocations(parameter.value());
      }

      if (selections == null) {
        selections = evaluateConfiguration();
      }

      if (selections == null && parameter.defaultValue().length > 0) {
        selections = parseLocations(parameter.defaultValue());
      }

      if (selections == null || selections.isEmpty()) {
        return;
      }

      Map<ResourceSelection<B>, List<Path>> resources = new LinkedHashMap<>();
      for (ResourceSelection<B> selection : selections) {
        List<Path> paths = select(selection.location, selection.includes, selection.excludes);
        if (!parameter.required() || !paths.isEmpty()) {
          resources.put(selection, paths);
        }
      }

      if (resources.isEmpty()) {
        return;
      }

      TypeAdapter type = parameter.originatingElement().getType();
      if (type.isArray() || type.isIterable()) {
        TypeAdapter parameterType = parameter.type();
        if (parameterType.isSameType(URL.class) || parameterType.isSameType(File.class)
            || parameterType.isSameType(Path.class)) {
          context.accept(newCollectionResourceValue(type, resources, parameterType));
        } else {
          context.accept(newCollectionValue(type, toListBucketValue(resources)));
        }
      } else if (resources.size() == 1) {
        Map.Entry<ResourceSelection<B>, List<Path>> entry = resources.entrySet().iterator().next();
        context.accept(newBucketValue(entry.getKey(), entry.getValue()));
      } else {
        throw new InvalidConfigurationException(context, "too many values");
      }
    }

    private List<Value<?>> toListBucketValue(Map<ResourceSelection<B>, List<Path>> resources) {
      return resources.entrySet().stream() //
          .map(e -> newBucketValue(e.getKey(), e.getValue())) //
          .collect(Collectors.toList());
    }

    final String relativePath(Path basedir, Path path) {
      return basedir.relativize(path).toString();
    }

    private List<Path> select(Path basedir, List<String> includes, List<String> excludes) {
      if (includes != null && includes.isEmpty()) {
        includes = null;
      }

      if (excludes != null && excludes.isEmpty()) {
        excludes = null;
      }

      if (workspace.isRegularFile(basedir)) {
        if (this instanceof InputFileSelector) {
          throw new InvalidConfigurationException(context, basedir + " is a regular file");
        }
        // TODO resource delta support, see takari BuildContext registerAndProcessInputs
        return selectFromJar(basedir, FileMatcher.subMatchers(Paths.get("/"), includes, excludes));
      } else if (workspace.isDirectory(basedir)) {
        // TODO resource delta support, see takari BuildContext registerAndProcessInputs
        return selectFromDirectory(FileMatcher.subMatchers(basedir, includes, excludes));
      } else {
        return Collections.emptyList();
      }
    }

    private List<Path> selectFromJar(Path jarPath, Map<Path, FileMatcher> subdirMatchers) {
      JarEntries jarEntries = JarEntriesCache.get().get(jarPath);

      return jarEntries.match(subdirMatchers);
    }

    private List<Path> selectFromDirectory(Map<Path, FileMatcher> matchers) {
      List<Path> resources = new ArrayList<>();
      matchers.forEach((subdir, matcher) -> {
        try (Stream<Path> paths = workspace.walk(subdir)) {
          paths //
              .filter(path -> matcher.matches(path)) //
              .forEach(resources::add);
        } catch (IOException e) {
          throw new InvalidConfigurationException(context, "could not list directory files", e);
        }
      });
      return resources;
    }

    List<ResourceSelection<B>> evaluateConfiguration() throws UncheckedIOException {
      Xpp3Dom configuration = context.configuration;
      if (configuration == null) {
        return null;
      }
      Xpp3Dom[] multivalue = configuration.getChildren(xmlMultivalue);
      if (multivalue.length > 0) {
        // TODO validate no other elements
        // TODO validate no value
        List<ResourceSelection<B>> buckets = new ArrayList<>();
        for (Xpp3Dom files : multivalue) {
          buckets.addAll(evaluateConfiguration(files, true));
        }
        return buckets;
      }
      return evaluateConfiguration(configuration, false);
    }

    final List<ResourceSelection<B>> evaluateConfiguration(Xpp3Dom configuration,
        boolean required) {
      List<String> includes = includes(configuration);
      List<String> excludes = excludes(configuration);
      if (configuration.getChildCount() == 0 && !isEmpty(configuration.getValue())) {
        Map.Entry<B, Path> e = evaluateLocation(configuration.getValue());
        return Collections
            .singletonList(new ResourceSelection<>(e.getKey(), e.getValue(), includes, excludes));
      }
      Xpp3Dom[] locations = getXmlList(configuration, xmlLocationPlural, xmlLocationSingular);
      if (locations != null && locations.length > 0) {
        return Stream.of(locations) //
            .map(this::evaluateLocation) //
            .map(p -> new ResourceSelection<>(p.getKey(), p.getValue(), includes, excludes)) //
            .collect(Collectors.toList());
      }
      if (required) {
        throw new InvalidConfigurationException(context,
            "<" + xmlLocationSingular + "> element is required");
      }
      return null;
    }

    final List<ResourceSelection<B>> parseLocations(String[] locations) {
      List<String> includes = includes(context.configuration);
      List<String> excludes = excludes(context.configuration);

      return context.evaluator().withValue(locations).asStrings(shouldAllowSourceRoots()) //
          .map(this::evaluateLocation) //
          .map(f -> new ResourceSelection<>(f.getKey(), f.getValue(), includes, excludes)) //
          .collect(Collectors.toList());
    }

    private List<String> evaluate(String[] strings) {
      return context.evaluator() //
          .withValue(strings) //
          .asStrings().collect(Collectors.toList());
    }

    /**
     * Returns elements of either nested or flat xml element list.
     */
    final Xpp3Dom[] getXmlList(Xpp3Dom element, String plural, String single) {
      if (element == null) {
        return null;
      }
      Xpp3Dom[] list = element.getChildren(plural);
      if (list.length > 1) {
        throw new InvalidConfigurationException(context, "only one <" + plural + "> is allowed");
      }
      Xpp3Dom[] flatlist = element.getChildren(single);
      if (flatlist.length > 0 && list.length > 0) {
        throw new InvalidConfigurationException(context,
            "Use <" + plural + "> or <" + single + ">");
      }
      if (list.length > 0) {
        flatlist = list[0].getChildren(single);
      }
      // TODO validate list has no other elements
      // TODO validate list has no value
      return flatlist;
    }

    /**
     * Evaluates and returns string list from three configuration sources
     * <ol>
     * <li>list of hardcoded values
     * <li>xml configuration in either nested or flat list format
     * <li>list of defaut values
     * </ol>
     */
    final List<String> mergeStringList(String[] hardcoded, String[] defaults, Xpp3Dom xml,
        String xmlPlural, String xmlSingle) {
      if (hardcoded.length > 0) {
        // TODO validate no configuration
        // TODO validate no defaultIncludes
        return evaluate(hardcoded);
      }

      Xpp3Dom[] flatlist = getXmlList(xml, xmlPlural, xmlSingle);
      if (flatlist != null && flatlist.length > 0) {
        // TODO validate configuration has no value
        return Stream.of(flatlist) //
            .map(x -> context.evaluator().evaluate(x.getValue())) //
            .collect(Collectors.toList());
      }

      if (defaults.length > 0) {
        return evaluate(defaults);
      }

      return null;
    }

    final List<String> includes(Xpp3Dom configuration) {
      List<String> includes = mergeStringList(parameter.includes(), parameter.defaultIncludes(),
          configuration, XML_CONFIG_INCLUDES, XML_CONFIG_INCLUDE);
      if (includes == null || includes.isEmpty()) {
        throw new InvalidConfigurationException(context, "<includes> is required");
      }
      return includes;
    }

    final List<String> excludes(Xpp3Dom configuration) {
      return mergeStringList(parameter.excludes(), parameter.defaultExcludes(), configuration,
          XML_CONFIG_EXCLUDES, XML_CONFIG_EXCLUDE);
    }

    final boolean containsOnly(Xpp3Dom element, String... children) {
      boolean contains = false;
      Set<String> invalid = new LinkedHashSet<>();
      for (Xpp3Dom child : element.getChildren()) {
        if (Stream.of(children).anyMatch(n -> n.equals(child.getName()))) {
          contains = true;
        } else {
          invalid.add(child.getName());
        }
      }
      if (contains && !invalid.isEmpty()) {
        throw new InvalidConfigurationException(context,
            "invalid configuration elements: " + invalid);
      }
      return contains;
    }
  }

  class InputFileSelector extends ResourceSelector<InputDirectoryValue, Path> {

    public InputFileSelector(InputDirectoryFilesParameter parameter) {
      super("files", "locations", "location", parameter);
    }

    @Override
    protected boolean shouldAllowSourceRoots() {
      return true;
    }

    @Override
    SimpleEntry<Path, Path> evaluateLocation(String location) throws UncheckedIOException {
      Path path = context.evaluator().toPath(location);
      return new SimpleEntry<>(path, path);
    }

    @Override
    SimpleEntry<Path, Path> evaluateLocation(Xpp3Dom location) throws UncheckedIOException {
      if (location.getChildCount() > 0) {
        throw new InvalidConfigurationException(context, "only value is allowed");
      }
      String value = location.getValue();
      if (value.equals(SOURCE_ROOTS_EXPR) || value.equals(TEST_SOURCE_ROOTS_EXPR)) {
        throw new InvalidConfigurationException(context,
            String.format("%s expression is not allowed in configuration", value));
      }
      return evaluateLocation(location.getValue());
    }

    @Override
    InputDirectoryValue newBucketValue(ResourceSelection<Path> selection, List<Path> paths) {
      Class<?> type = ((ReflectionType) parameter.type()).adaptee();
      Path basedir = selection.bucket;
      List<String> includes = selection.includes;
      List<String> excludes = selection.excludes;
      TreeSet<Path> files = new TreeSet<>();
      TreeSet<String> filenames = new TreeSet<>();
      if (paths != null) {
        for (int i = 0; i < paths.size(); i++) {
          files.add(paths.get(i));
          filenames.add(relativePath(selection.location, paths.get(i)));
        }
      }
      return new InputDirectoryValue(type, basedir, includes, excludes, files, filenames);
    }

    @Override
    Value<?> newCollectionResourceValue(TypeAdapter type,
        Map<ResourceSelection<Path>, List<Path>> resources, TypeAdapter parameterType) {
      List<Path> files = resources.values().stream() //
          .flatMap(list -> list.stream()) //
          .collect(Collectors.toList());
      return new InputFilesValue(((ReflectionType) type).multivalueFactory(), files,
          ((ReflectionType) parameterType));
    }
  }

  static class ArtifactLocation {

    final IArtifactMetadata metadata;

    /**
     * The artifact jar file or {@code null} for exploded directory-based artifacts.
     */
    final Path jar;

    public ArtifactLocation(IArtifactMetadata artifact, Path jar) {
      this.metadata = artifact;
      this.jar = jar;
    }
  }

  class BaseArtifactResourceSelector
      extends ResourceSelector<ArtifactResourcesValue, ArtifactLocation> {

    public BaseArtifactResourceSelector(String multivalue, String locationPlural,
        String locationSingular, AbstractResourceSelectionParameter parameter) {
      super(multivalue, locationPlural, locationSingular, parameter);
    }

    private String evaluate(Xpp3Dom element, String childName, boolean required) {
      Xpp3Dom child = element.getChild(childName);
      if (child == null) {
        if (required) {
          throw new InvalidConfigurationException(context, "<" + childName + "> is required");
        }
        return null;
      }
      if (child.getChildCount() > 0) {
        throw new InvalidConfigurationException(context,
            "<" + childName + "> must not have child elements");
      }
      if (isEmpty(child.getValue())) {
        if (required) {
          throw new InvalidConfigurationException(context, "<" + childName + "> value is required");
        }
        return null;
      }
      return context.evaluator().evaluate(child.getValue());
    }

    @Override
    Map.Entry<ArtifactLocation, Path> evaluateLocation(Xpp3Dom location)
        throws UncheckedIOException {
      if (location.getChildCount() > 0 && !isEmpty(location.getValue())) {
        throw new InvalidConfigurationException(context, "both elements and value are not allowed");
      }
      if (!isEmpty(location.getValue())) {
        return evaluateLocation(location.getValue());
      }
      // TODO validate no other elements
      String groupId = evaluate(location, "groupId", true);
      String artifactId = evaluate(location, "artifactId", true);
      String classifier = evaluate(location, "classifier", false);
      return evaluateLocation(groupId, artifactId, classifier);
    }

    private Map.Entry<ArtifactLocation, Path> evaluateLocation(String groupId, String artifactId,
        String classifier) {
      // TODO Use artifact resolver instead of a dependency resolver to support any artifacts not
      // just
      // dependency artifacts.
      Map.Entry<IArtifactMetadata, Path> dependency =
          dependencyResolver.getProjectDependency(groupId, artifactId, classifier);
      if (dependency == null || dependency.getValue() == null
          || !(workspace.isRegularFile(dependency.getValue())
              || workspace.isDirectory(dependency.getValue()))) {
        String gac = groupId + ":" + artifactId;
        if (classifier != null) {
          gac = gac + ":" + classifier;
        }
        throw new InvalidConfigurationException(context, "dependency " + gac + " does not exist");
      }
      return toArtifactLocation(dependency);
    }

    protected Map.Entry<ArtifactLocation, Path> toArtifactLocation(
        Map.Entry<IArtifactMetadata, Path> dependency) {
      Path location = dependency.getValue();
      IArtifactMetadata metadata = dependency.getKey();
      ArtifactLocation artifact =
          new ArtifactLocation(metadata, workspace.isRegularFile(location) ? location : null);
      return new SimpleEntry<>(artifact, location);
    }

    @Override
    Map.Entry<ArtifactLocation, Path> evaluateLocation(String location)
        throws UncheckedIOException {
      StringTokenizer st = new StringTokenizer(location, ":");
      String groupId = st.nextToken();
      String artifactId = st.nextToken();
      String classifier = st.hasMoreTokens() ? st.nextToken() : null;
      return evaluateLocation(groupId, artifactId, classifier);
    }

    @Override
    Value<?> newCollectionResourceValue(TypeAdapter type,
        Map<ResourceSelection<ArtifactLocation>, List<Path>> resources, TypeAdapter parameterType) {

      List<ArtifactResourcesValue> values = resources.entrySet().stream() //
          .map(e -> newBucketValue(e.getKey(), e.getValue())) //
          .collect(Collectors.toList());

      return new ListArtifactResourcesValue(((ReflectionType) type).multivalueFactory(), values);
    }

    @Override
    ArtifactResourcesValue newBucketValue(ResourceSelection<ArtifactLocation> selection,
        List<Path> paths) {
      // notes on file handle leaks and performance
      // * each FileSystems.newFileSystem(jar) creates new open file handle (OSX, Java 1.8.0_102)
      // * need to explicitly close open filesystems to avoid file handle leak
      // * it is not possible to use Path from a closed filesystem
      // to avoid resource leaks, the filesystem instances are closed as soon as possible and the
      // clients will effectively get jar:file URL instances
      // by default JDK caches URLConnection, so performance will be comparable with
      // open filesystem access (reading all junit-4.12.jar entries 1000 times using filesystem
      // takes ~5.5 seconds, using URL ~6.8 seconds).
      // if URLConnection cache is disabled, reading all junit-4.12.jar entries 1000 times takes
      // ~22.2 seconds. this is still acceptable assuming only a small number of jar file entries
      // will be used through URL API.
      try {
        ArtifactLocation artifact = selection.bucket;
        List<URL> urls = new ArrayList<>();
        boolean isSelectionRegularFile = workspace.isRegularFile(selection.location);

        for (Path resource : paths) {
          URL url;
          String relpath;

          if (isSelectionRegularFile) {
            relpath = resource.toString();
            url = (new URI("jar:" + selection.location.toUri() + "!/" + relpath)).toURL();
          } else {
            relpath = relativePath(selection.location, resource);
            url = resource.toUri().toURL();
          }
          urls.add(ArtifactResourceURLStreamHandler.newURL(artifact.metadata, relpath, url));
        }
        Set<Path> files = new TreeSet<>();
        if (artifact.jar != null) {
          files.add(artifact.jar);
        } else {
          paths.stream().forEach(p -> files.add(p));
        }
        return new ArtifactResourcesValue(files, artifact.metadata, urls);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }
  }

  class DependencyResourceSelector extends BaseArtifactResourceSelector {

    public DependencyResourceSelector(AbstractResourceSelectionParameter parameter) {
      super("resources", "dependencies", "dependency", parameter);
    }

    @Override
    List<ResourceSelection<ArtifactLocation>> evaluateConfiguration() throws UncheckedIOException {
      List<ResourceSelection<ArtifactLocation>> buckets = super.evaluateConfiguration();
      if (buckets == null) {
        List<String> includes = includes(context.configuration);
        List<String> excludes = excludes(context.configuration);
        buckets = dependencyResolver.getProjectDependencies(true).entrySet().stream() //
            .map(this::toArtifactLocation) //
            .map(e -> new ResourceSelection<>(e.getKey(), e.getValue(), includes, excludes)) //
            .collect(Collectors.toList());
      }
      return buckets;
    }
  }

  class ArtifactResourceSelector extends BaseArtifactResourceSelector {

    public ArtifactResourceSelector(ArtifactResourcesParameter parameter) {
      super("resources", "artifacts", "artifact", parameter);
    }
  }

  @Override
  public boolean enterMultivalue(MultivalueParameter metadata) {
    context = context.multivalueSubcontext(metadata.elements.required());

    if (context.configuration == null) {
      metadata.elements.accept(this);
    } else {
      Xpp3Dom configuration = context.configuration;

      // TODO validate configuration does not have value
      // TODO validate configuration has children

      for (Xpp3Dom subConfiguration : configuration.getChildren()) {
        context = ((MultivalueContext) context).elementSubcontext(subConfiguration);
        metadata.elements.accept(this);
        Value<?> element = context.value();
        context = context.parent;
        if (element != null) {
          context.accept(element);
        }
      }
    }

    List<Value<?>> elements = context.values();
    context = context.parent;
    if (elements != null && !elements.isEmpty()) {
      context.accept(newCollectionValue(metadata.type(), elements));
    }

    return false;
  }

  private CollectionValue newCollectionValue(TypeAdapter type, List<Value<?>> elements) {
    return new CollectionValue(((ReflectionType) type).multivalueFactory(), elements);
  }

  @Override
  public void visitUnsupportedCollection(UnsupportedCollectionParameter metadata) {
    throw new IllegalArgumentException(); // should have been reported during validation
  }

  @SuppressWarnings("unchecked")
  @Override
  public void visitMap(MapParameter metadata) {

    if (context.configuration == null) {
      if (metadata.required()) {
        throw new InvalidConfigurationException(context,
            "configuration is required for a Map parameter");
      }
      return;
    }

    if (!isEmpty(context.configuration.getValue())) {
      throw new InvalidConfigurationException(context, "configuration value is not allowed");
    }

    if (context.configuration.getChildCount() < 1) {
      throw new InvalidConfigurationException(context, "configuration requires at least one child");
    }

    Xpp3Dom configuration = context.configuration;

    Map<String, String> elements = new LinkedHashMap<>();
    for (Xpp3Dom subConfiguration : configuration.getChildren()) {
      elements.put(subConfiguration.getName(), subConfiguration.getValue());
    }
    if (!elements.isEmpty()) {
      Class<?> type = ((ReflectionType) metadata.type()).adaptee();
      Function<String, ?> converter =
          SimpleParameter.getConverter(metadata.originatingElement().getParameterTypes().get(1));
      InstanceFactory<Map<String, Object>> supplier;
      if (!type.isInterface()) {
        Constructor<Map<?, ?>> constructor = getMapConstructor(type);
        supplier = () -> (Map<String, Object>) constructor.newInstance();
      } else {
        supplier = LinkedHashMap::new;
      }
      context.accept(new MapValue(supplier, elements, converter));
    }
  }

  @SuppressWarnings("unchecked")
  private static Constructor<Map<?, ?>> getMapConstructor(Class<?> type) {
    try {
      return (Constructor<Map<?, ?>>) type.getConstructor();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(); // should have been caught by validation
    }
  }

  @Override
  public boolean enterComposite(CompositeParameter metadata) {
    if (context.configuration == null) {
      return false;
    }

    if (!isEmpty(context.configuration.getValue())) {
      throw new InvalidConfigurationException(context, "configuration value is not allowed");
    }

    Map<Field, Value<?>> members = new HashMap<>();
    for (AbstractParameter member : metadata.members) {
      context = context.singletonSubcontext(member.name(), member.required());
      member.accept(this);
      if (context.value() != null) {
        ReflectionField element = (ReflectionField) member.originatingElement();
        members.put(element.adaptee(), context.value());
      }
      context = context.parent;
    }

    if (!members.isEmpty()) {
      ReflectionType element = (ReflectionType) metadata.type();
      Class<?> type = element.adaptee();
      context.accept(new CompositeValue(type, members));
    }

    return false;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Enum<?> enumValue(Class<?> enumType, String value) {
    return Enum.valueOf((Class) enumType, value);
  }

  @Override
  public void visitSimple(SimpleParameter metadata) {
    ReflectionType type = (ReflectionType) metadata.type();
    Function<String, ?> converter;
    if (type.isEnum()) {
      converter = v -> enumValue(type.adaptee(), v);
    } else {
      converter = SimpleParameter.getConverter(type);
    }

    context.evaluator() //
        .withValue(metadata.value()) //
        .withConfiguration(context.configuration) //
        .withDefaultValue(metadata.defaultValue()) //
        .asStrings().forEach(s -> context.accept(new StringValue(s, converter)));
  }

  @Override
  public boolean enterBuilderClass(BuilderClass metadata) {
    context = new SingletonContext(null, metadata.type().simpleName(), builderConfiguration, false);
    for (AbstractParameter parameter : metadata.parameters()) {
      Value<?> value = forcedParameters.get(parameter.name());
      if (value == null) {
        context = context.singletonSubcontext(parameter.name(), parameter.required());
        parameter.accept(this);
        value = context.value();
        context = context.parent;
      }
      if (value != null) {
        ReflectionField element = (ReflectionField) parameter.originatingElement();
        parameters.put(element.adaptee(), value);
      }
    }
    // this is noop in production
    for (String name : forcedParameters.keySet()) {
      if (!metadata.parameters().stream().anyMatch(p -> name.equals(p.name()))) {
        throw new IllegalArgumentException("no such parameter: " + name);
      }
    }
    for (BuilderMethod builder : metadata.builders()) {
      if (builder.annotation().name().equals(goal) && builder.isNonDeterministic()) {
        isNonDeterministic = true;
      }
    }
    context = null;
    return false;
  }

  @Override
  public void visitBuilder(BuilderMethod metadata) {}

  @Override
  public void visitInputDirectory(InputDirectoryParameter metadata) {
    Class<?> type = ((ReflectionType) metadata.type()).adaptee();
    List<String> includes = Arrays.asList(metadata.includes());
    List<String> excludes = Arrays.asList(metadata.excludes());

    if (context.configuration != null) {
      if (metadata.value().length > 0) {
        throw new InvalidConfigurationException(context, "configuration not allowed");
      }

      context.configuration().forEach(configuration -> {
        // TODO validate only allowed configuration elements are present
        Path location = context.evaluator() //
            .withConfiguration(configuration.getChild("location")) //
            .asPath();
        if (location == null) {
          throw new InvalidConfigurationException(context, "<location> is required");
        }
        pushInputDirectory(type, metadata.required(), location, includes, excludes);
      });
    } else {
      context.evaluator() //
          .withValue(metadata.value()) //
          .withDefaultValue(metadata.defaultValue()) //
          .asPathsWithSourceRoots()
          .forEach(f -> pushInputDirectory(type, metadata.required(), f, includes, excludes));
    }
  }

  @Override
  public void visitInputDirectoryFiles(InputDirectoryFilesParameter metadata) {
    new InputFileSelector(metadata).select();
  }

  private void pushInputDirectory(Class<?> type, boolean required, Path location,
      List<String> includes, List<String> excludes) {
    if (includes.isEmpty()) {
      includes = null;
    }
    if (excludes.isEmpty()) {
      excludes = null;
    }
    if (workspace.isRegularFile(location)) {
      throw new InvalidConfigurationException(context, location + " is a regular file");
    }
    if (!workspace.isDirectory(location)) {
      if (!required) {
        context.accept(new InputDirectoryValue(type, location, includes, excludes,
            Collections.emptySet(), Collections.emptySet()));
      }

      return;
    }

    // TODO resource delta support, see takari BuildContext registerAndProcessInputs
    TreeSet<Path> files = new TreeSet<>();
    TreeSet<String> filenames = new TreeSet<>();
    FileMatcher.subMatchers(location, includes, excludes).forEach((subdir, matcher) -> {
      try (Stream<Path> paths = workspace.walk(subdir)) {
        paths.filter(path -> workspace.exists(path)) //
            .filter(path -> matcher.matches(path)) //
            .forEach(path -> {
              files.add(path);
              filenames.add(subdir.relativize(path).toString());
            });
      } catch (IOException e) {
        throw new InvalidConfigurationException(context, "could not list directory files", e);
      }
    });
    if (!required || !files.isEmpty()) {
      context.accept(new InputDirectoryValue(type, location, includes, excludes, files, filenames));
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void visitDependencies(DependenciesParameter metadata) {
    if (context.configuration != null) {
      throw new InvalidConfigurationException(context, "configuration not allowed");
    }

    Class<?> type = ((ReflectionType) metadata.type()).adaptee();
    Map<IArtifactMetadata, Path> dependencyMap =
        dependencyResolver.getProjectDependencies(metadata.transitive());

    if (dependencyMap.isEmpty()) {
      return;
    }

    if (metadata.originatingElement().getType().isMap()) {
      InstanceFactory<Map<IArtifactMetadata, Object>> supplier;
      if (!type.isInterface()) {
        Constructor<Map<?, ?>> constructor = getMapConstructor(type);
        supplier = () -> (Map<IArtifactMetadata, Object>) constructor.newInstance();
      } else {
        supplier = LinkedHashMap::new;
      }
      context.accept(new DependencyMapValue(((ReflectionType) metadata.elementType()).adaptee(),
          supplier, dependencyMap));
    } else {
      List<Value<?>> values = dependencyMap.entrySet().stream() //
          .map(e -> new DependencyValue(type, e.getKey(), e.getValue())) //
          .collect(Collectors.toList());
      context.accept(newCollectionValue(metadata.originatingElement().getType(), values));
    }

  }

  private void visitFileParameter(AbstractFileParameter<?> metadata, boolean checkExists,
      Function<Path, Value<?>> factory) {
    context.evaluator() //
        .withValue(metadata.value()) //
        .withConfiguration(context.configuration) //
        .withDefaultValue(metadata.defaultValue()) //
        .asPaths() //
        .filter(p -> !checkExists || workspace.exists(p)) //
        .forEach(f -> context.accept(factory.apply(f)));
  }

  @Override
  public void visitOutputDirectory(OutputDirectoryParameter metadata) {
    Class<?> type = ((ReflectionType) metadata.type()).adaptee();
    visitFileParameter(metadata, false, f -> new OutputDirectoryValue(type, f));
  }

  @Override
  public void visitOutputFile(OutputFileParameter metadata) {
    Class<?> type = ((ReflectionType) metadata.type()).adaptee();
    visitFileParameter(metadata, false, f -> new OutputFileValue(type, f));
  }

  @Override
  public void visitGeneratedResourcesDirectory(GeneratedResourcesDirectoryParameter metadata) {
    Class<?> type = ((ReflectionType) metadata.type()).adaptee();
    Xpp3Dom configuration = context.configuration;
    List<String> includes = context.evaluator() //
        .withValue(metadata.includes()) //
        .withConfiguration(configuration != null && configuration.getChildCount() > 0
            ? configuration.getChild(BuilderInputsBuilder.XML_CONFIG_INCLUDES)
            : null) //
        .withDefaultValue(metadata.defaultIncludes()) //
        .asStrings().collect(Collectors.toList());
    List<String> excludes = context.evaluator() //
        .withValue(metadata.excludes()) //
        .withConfiguration(configuration != null && configuration.getChildCount() > 0
            ? configuration.getChild(BuilderInputsBuilder.XML_CONFIG_EXCLUDES)
            : null) //
        .withDefaultValue(metadata.defaultExcludes()) //
        .asStrings().collect(Collectors.toList());
    Path location = context.evaluator() //
        .withValue(metadata.value()) //
        .withConfiguration(configuration != null && configuration.getChildCount() > 0
            ? configuration.getChild(BuilderInputsBuilder.XML_CONFIG_LOCATION)
            : configuration) //
        .withDefaultValue(metadata.defaultValue()) //
        .asPath();

    context.accept(new GeneratedResourcesDirectoryValue(type, location, metadata.getResourceType(),
        includes, excludes));
  }

  @Override
  public void visitGeneratedSourcesDirectory(GeneratedSourcesDirectoryParameter metadata) {
    Class<?> type = ((ReflectionType) metadata.type()).adaptee();
    GeneratedSourcesDirectory ann = metadata.annotation();
    visitFileParameter(metadata, false,
        f -> new GeneratedSourcesDirectoryValue(type, f, ann.sourceType()));
  }

  @Override
  public void visitInputFile(InputFileParameter metadata) {
    Class<?> type = ((ReflectionType) metadata.type()).adaptee();
    visitFileParameter(metadata, metadata.required(), f -> {
      if (workspace.isDirectory(f)) {
        throw new InvalidConfigurationException(context, f + " is a directory");
      }
      return new InputFileValue(type, f);
    });
  }

  @Override
  public void visitDependencyResources(DependencyResourcesParameter metadata) {
    new DependencyResourceSelector(metadata).select();
  }

  @Override
  public void visitArtifactResources(ArtifactResourcesParameter metadata) {
    new ArtifactResourceSelector(metadata).select();
  }

}
