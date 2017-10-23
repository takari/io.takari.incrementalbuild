package io.takari.builder.internal;

import static io.takari.builder.enforcer.internal.EnforcerConfig.ALL_BUILDERS;
import static io.takari.builder.internal.pathmatcher.PathNormalizer.normalize0;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;

import io.takari.builder.Builder;
import io.takari.builder.ResourceType;
import io.takari.builder.enforcer.internal.EnforcerConfig;
import io.takari.builder.enforcer.internal.EnforcerViolation;
import io.takari.builder.internal.BuilderExecutionState.InprogressStateWriter;
import io.takari.builder.internal.BuilderInputs.Digest;
import io.takari.builder.internal.Message.MessageSeverity;
import io.takari.builder.internal.digest.ClasspathDigester;
import io.takari.builder.internal.digest.FileDigest;
import io.takari.builder.internal.digest.SHA1Digester;
import io.takari.builder.internal.pathmatcher.PathMatcher;
import io.takari.incrementalbuild.workspace.MessageSink;
import io.takari.incrementalbuild.workspace.MessageSink.Severity;
import io.takari.incrementalbuild.workspace.Workspace;

public class BuilderRunner {
  private final Logger log;
  private final Class<?> builderType;
  private final String goal;

  // build session base directory, typically maven multimodule project base directory
  private Path sessionBasedir;

  // this builder base directory, typically maven project base directory.
  private Path projectBasedir;

  // incremental build state file location, can be null
  private Path stateFile;

  // this builder classpath, used to allow reads from classpath and to detect classpath changes
  private List<Path> classpath = Collections.emptyList();

  private ClasspathDigester classpathDigester;

  // Matcher of jvm and maven core classpath entries, used to allow reads from classpath
  private PathMatcher sessionClasspathMatcher;

  // resolver of @Dependency* annotations, used to compute builder inputs
  private DependencyResolver dependencyResolver;

  private Xpp3Dom configuration;

  // build user and system properties, takes precedence over propertyResolver
  private Map<String, String> properties = new HashMap<>();

  // project property resolver
  private Function<String, String> propertyResolver;

  // project resources. will add captured resources to these
  private Consumer<ResourceRoot> resourceConsumer;

  // project compile source roots. will add captured roots to these
  private List<String> compileSourceRoots;

  // project test compile source roots. will add captured roots to these
  private List<String> testCompileSourceRoots;

  // Builder whitelist file
  private EnforcerConfig enforcerConfig;

  // Builder identifier, in the format groupId:artifactId:goal
  private String builderId;

  // workspace
  private Workspace workspace;

  // message sink inplementation
  private MessageSink messageSink;

  //
  // // Whether the builder has been declared to be a non-deterministic builder (allows
  // whitelisting)
  // private boolean isNonDeterministicBuilder;

  // default location of builder messages, typically location of pom.xml <execution> element
  private Path defaultFile;
  private int defaultLine;
  private int defaultColumn;

  // forced parameter values
  private Map<String, BuilderInputs.Value<?>> forcedParameters = Collections.emptyMap();

  private BuilderRunner(Logger log, Class<?> builderType, String goal) {
    this.log = log;
    this.builderType = builderType;
    this.goal = goal;
  }

  //
  // Construction
  //

  public static BuilderRunner create(Logger log, Class<?> builderType, String goal) {
    return new BuilderRunner(log, builderType, goal);
  }

  public BuilderRunner setSessionBasedir(Path basedir) {
    this.sessionBasedir = basedir;
    return this;
  }

  public BuilderRunner setProjectBasedir(Path basedir) {
    this.projectBasedir = basedir;
    return this;
  }

  public BuilderRunner setStateFile(Path stateFile) {
    this.stateFile = stateFile;
    return this;
  }

  public BuilderRunner setSessionClasspathMatcher(PathMatcher matcher) {
    this.sessionClasspathMatcher = matcher;
    return this;
  }

  public BuilderRunner setClasspath(Collection<Path> classpath, ClasspathDigester digester) {
    this.classpath = new ArrayList<>(classpath);
    this.classpathDigester = digester;
    return this;
  }

  public BuilderRunner setDependencyResolver(DependencyResolver dependencyResolver) {
    this.dependencyResolver = dependencyResolver;
    return this;
  }

  public BuilderRunner setConfiguration(Xpp3Dom configuration) {
    this.configuration = configuration;
    return this;
  }

  public BuilderRunner setDefaultMessageLocation(Path file, int line, int column) {
    this.defaultFile = file;
    this.defaultLine = line;
    this.defaultColumn = column;
    return this;
  }

  public BuilderRunner setProjectProperties(Function<String, String> resolver) {
    this.propertyResolver = resolver;
    return this;
  }

  public BuilderRunner setProjectResourcesConsumer(Consumer<ResourceRoot> resourceConsumer) {
    this.resourceConsumer = resourceConsumer;
    return this;
  }

  public BuilderRunner setProjectCompileSourceRoots(List<String> compileSourceRoots) {
    this.compileSourceRoots = compileSourceRoots;
    return this;
  }

  public BuilderRunner setProjectTestCompileSourceRoots(List<String> testCompileSourceRoots) {
    this.testCompileSourceRoots = testCompileSourceRoots;
    return this;
  }

  public BuilderRunner setSessionProperties(Properties system, Properties user) {
    BiConsumer<Object, Object> action = (key, value) -> {
      if (key instanceof String && value instanceof String) {
        properties.put((String) key, (String) value);
      }
    };
    system.forEach(action);
    user.forEach(action);
    return this;
  }

  public BuilderRunner setForcedParameters(Map<String, BuilderInputs.Value<?>> forcedParameters) {
    this.forcedParameters = forcedParameters;

    return this;
  }

  public BuilderRunner setBuilderEnforcerConfig(EnforcerConfig enforcerConfig) {
    this.enforcerConfig = enforcerConfig;
    return this;
  }

  public BuilderRunner setBuilderId(String builderId) {
    this.builderId = builderId;
    return this;
  }

  public BuilderRunner setWorkspace(Workspace workspace) {
    this.workspace = workspace;
    return this;
  }

  public BuilderRunner setMessageSink(MessageSink messageSink) {
    this.messageSink = messageSink;
    return this;
  }

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
      return projectBasedir;
    }
  };

  //
  // execution
  //

  @FunctionalInterface
  public static interface ExceptionFactory<E extends Exception> {
    E exception(String message, Throwable cause);
  }

  public <E extends Exception> BuilderContext execute(ExceptionFactory<E> efactory) throws E {
    ExpressionEvaluator evaluator =
        new ExpressionEvaluator(Arrays.asList(s -> properties.get(s), propertyResolver));

    /*
     * All requested output file writes are recorded in undo log file *before* the builder is
     * allowed the write. The log file is deleted after graceful builder execution termination. If
     * jvm crashes (or is killed) during builder execution, the log file is used to cleanup all
     * output files written during the failed execution.
     */
    final Path inprogressFile = stateFile != null //
        ? stateFile.getParent().resolve(stateFile.getFileName() + "-undo") //
        : null;
    if (inprogressFile != null && Files.exists(inprogressFile)) {
      // previous execution did not finish gracefully because jvm crashed or was killed
      // delete all output files created during the crashed execution and trigger rebuild

      try {
        // delete inprogress output files
        deleteOutputs(BuilderExecutionState.readInprogressOutputPaths(inprogressFile), efactory);

        // if exists, delete state file to trigger rebuild
        Files.deleteIfExists(stateFile);

        // the undo is complete, delete inprogress file
        Files.delete(inprogressFile);
      } catch (IOException e) {
        throw efactory.exception(
            "Unrecoverable incremental build error while attempting to recover from prior builder execution failure",
            e);
      }
    }

    BuilderExecutionState oldExecutionState = BuilderExecutionState.load(stateFile);

    BuilderWorkspace builderWorkspace =
        new BuilderWorkspace(workspace, projectModelProvider.getBasedir(), oldExecutionState);

    final MessageCollector messages = new MessageCollector(log);



    final BuilderInputs inputs;
    try {
      inputs = BuilderInputsBuilder.build(goal, projectModelProvider, dependencyResolver, evaluator,
          builderType, configuration, forcedParameters, builderWorkspace);
    } catch (IOException e) {
      throw efactory.exception("Could not compute builder inputs", e);
    }

    if (workspace.getMode().equals(Workspace.Mode.SUPPRESSED)) {
      return skippedBuilderExecution(efactory, oldExecutionState, inputs, messages);
    }

    final Serializable classpathDigest;
    try {
      classpathDigest = classpathDigester.digest(classpath);
    } catch (IOException e) {
      throw efactory.exception("Could not compute classpath digest", e);
    }

    final Collection<String> readAndTrackExceptions;
    try {
      readAndTrackExceptions = getReadAndTrackExceptions();
    } catch (ExpressionEvaluationException e) {
      throw efactory.exception("Unable to evaluate Read and Track exceptions", e);
    }

    final Digest inputsDigest = inputs.getDigest();
    if (!workspace.getMode().equals(Workspace.Mode.ESCALATED)
        && inputsDigest.equals(oldExecutionState.inputsDigest) //
        && getExceptionsDigest(readAndTrackExceptions).equals(oldExecutionState.exceptionsDigest)
        && propertiesDigest(oldExecutionState.properties.keySet())
            .equals(oldExecutionState.properties) //
        && classpathDigest.equals(oldExecutionState.classpathDigest)) {

      return skippedBuilderExecution(efactory, oldExecutionState, null, messages);
    }

    deleteOutputs(oldExecutionState.outputPaths, efactory);

    inputs.getResourceRoots().forEach(this.resourceConsumer);
    inputs.getCompileSourceRoots().forEach(this::addCompileSourceRootToProject);

    try {
      for (Path file : inputs.getOutputDirectories()) {
        Files.createDirectories(file);
        workspace.processOutput(file.toFile());
      }
      for (Path file : inputs.getOutputFiles()) {
        Files.createDirectories(file.getParent());
        workspace.processOutput(file.toFile());
      }
    } catch (IOException e) {
      throw efactory.exception("Unable to create Output Directories", e);
    }

    BuilderContext.Builder contextBuilder =
        BuilderContext.builder(log, goal, sessionBasedir(), messages, builderWorkspace);
    // allow read from global classpath entries
    if (sessionClasspathMatcher != null) {
      contextBuilder.addInputMatcher(sessionClasspathMatcher);
    }
    // allow read from classpath entries
    classpath.forEach(f -> {
      if (Files.isDirectory(f)) {
        contextBuilder.addInputDirectory(f);
      } else {
        contextBuilder.addInputFile(f);
      }
    });

    if (inputs.isNonDeterministic()) {
      contextBuilder.addReadExceptions(enforcerConfig.getReadExceptions(builderId));
      contextBuilder.addReadAndTrackExceptions(readAndTrackExceptions);
      contextBuilder.addWriteExceptions(enforcerConfig.getWriteExceptions(builderId));
      contextBuilder.addExecExceptions(enforcerConfig.getExecExceptions(builderId));
      contextBuilder.setNetworkAccessAllowed(enforcerConfig.allowNetworkAccess(builderId));
    } else if (enforcerConfig.hasEntriesFor(builderId)) {
      throw efactory.exception(String.format(
          "Found whitelist entries in.mvn/builder-whitelist.config for builder not annotated with @NonDeterministic: %s",
          builderId), null);
    } else if (enforcerConfig.hasWildcardEntries()) {
      contextBuilder.addReadExceptions(enforcerConfig.getReadExceptions(ALL_BUILDERS));
      contextBuilder.addWriteExceptions(enforcerConfig.getWriteExceptions(ALL_BUILDERS));
      contextBuilder.addExecExceptions(enforcerConfig.getExecExceptions(ALL_BUILDERS));
    }

    // allow filesystem access according to declared builder inputs and outputs
    contextBuilder.addInputFiles(inputs.getInputFiles());
    inputs.getOutputDirectories().forEach(d -> contextBuilder.addOutputDirectory(d));
    inputs.getOutputFiles().forEach(f -> contextBuilder.addOutputFile(f));
    String tempDir = System.getProperty("java.io.tmpdir");
    if (tempDir != null && !tempDir.isEmpty()) {
      // need to consider both absolute and canonical paths (i.e. with symlinks resolved)
      contextBuilder.addTemporaryDirectory(Paths.get(tempDir));
      contextBuilder.addTemporaryDirectory(Paths.get(normalize0(tempDir)));
    }

    InprogressStateWriter inprogressWriter = BuilderExecutionState.NOOP_INPROGRESSWRITER;
    if (inprogressFile != null) {
      try {
        inprogressWriter = BuilderExecutionState.newInprogressWriter(inprogressFile);
      } catch (IOException e) {
        throw efactory.exception("Could not persist incremental build state", e);
      }
    }
    contextBuilder.setInprogressWriter(inprogressWriter);

    final BuilderContext builderContext = contextBuilder.build();

    try {
      builderContext.enter();

      try {
        Object builderInstance = inputs.newBuilder();

        Method builderMethod = getBuilderMethodForGoal(builderType, goal, efactory);

        builderMethod.invoke(builderInstance);

        // NB: keep temporary files if the builder failed, useful for debugging
        for (String file : builderContext.getTemporaryFiles()) {
          Path filePath = Paths.get(file);
          if (Files.isDirectory(filePath)) {
            FileUtils.deleteDirectory(file);
          } else {
            Files.deleteIfExists(filePath);
          }
        }
      } catch (ReflectiveOperationException | IllegalArgumentException | IOException e) {
        if (e.getCause() instanceof IncrementalBuildException) {
          // could not write builder execution undo log file, terminate immediately
          throw efactory.exception("Could not persist incremental build state",
              e.getCause().getCause());
        }
        Throwable executionFailure = getRootCause(e);
        if (executionFailure instanceof Error) {
          throw (Error) executionFailure; // assume errors are really bad, let the
                                          // outer guy deal with them
        }
        messages.error(defaultFile, defaultLine, defaultColumn, executionFailure.getMessage(),
            executionFailure);
      }

    } finally {
      builderContext.leave();
    }

    // avoid open file leaks in case of builder enforcement violations
    try {
      inprogressWriter.close();
    } catch (IOException e) {
      throw efactory.exception("Could not persist incremental build state", e);
    }

    // TODO decide if violations should be persisted/replayed as other build errors
    Set<EnforcerViolation> violations = builderContext.getViolations();
    if (!violations.isEmpty()) {
      throw new SecurityException(getFormattedViolationsMessage(violations, builderContext));
    }

    List<Message> collectedMessages = messages.getCollectedMessages();

    if (stateFile != null) {
      try {
        BuilderExecutionState.store(stateFile, //
            inputsDigest, //
            propertiesDigest(builderContext.getReadProperties()), //
            classpathDigest, //
            getWrittenFilesForDeletion(builderContext), //
            inputs.getCompileSourceRoots(), //
            inputs.getResourceRoots(), //
            collectedMessages, //
            getExceptionsDigest(readAndTrackExceptions));

        // delete inprogress file after execute state was persisted
        // the execution has fully completed and undo will not be necessary
        Files.delete(inprogressFile);
      } catch (IOException e) {
        throw efactory.exception("Could not persist incremental build state", e);
      }
    }

    if (messageSink != null) {
      clearStaleMessages(oldExecutionState);
      collectedMessages.forEach(m -> messageSink.message(new File(m.file), m.line, m.column,
          m.message, toMessageSinkSeverity(m.severity), m.cause));

    } else {
      messages.throwExceptionIfThereWereErrorMessages(efactory);
    }

    return builderContext;
  }

  private <E extends Exception> BuilderContext skippedBuilderExecution(ExceptionFactory<E> efactory,
      BuilderExecutionState oldExecutionState, BuilderInputs inputs,
      final MessageCollector messages) throws E {

    if (inputs != null) {
      inputs.resourceRoots.forEach(this.resourceConsumer);
      inputs.compileSourceRoots.forEach(this::addCompileSourceRootToProject);
    } else {
      oldExecutionState.resourceRoots.forEach(this.resourceConsumer);
      oldExecutionState.compileSourceRoots.forEach(this::addCompileSourceRootToProject);
    }

    messages.replayMessages(efactory, oldExecutionState.messages); // fails the build if there
                                                                   // were errors
    return null;
  }

  private void clearStaleMessages(BuilderExecutionState oldExecutionState) {
    Digest oldDigest = oldExecutionState.inputsDigest;
    Collection<String> oldOutputs = oldExecutionState.outputPaths;

    if (oldDigest != null) {
      oldDigest.files().forEach(f -> messageSink.clearMessages(f.toFile()));
    }

    if (oldOutputs != null) {
      oldOutputs.forEach(o -> messageSink.clearMessages(new File(o)));
    }

    messageSink.clearMessages(new File(projectBasedir.toFile(), "pom.xml"));
  }

  private static Severity toMessageSinkSeverity(MessageSeverity severity) {
    switch (severity) {
      case ERROR:
        return Severity.ERROR;
      case WARNING:
        return Severity.WARNING;
      default:
        return Severity.INFO;
    }
  }

  // delete old output files to make sure no obsolete output files are left behind
  private <E extends Exception> void deleteOutputs(Collection<String> outputPaths,
      ExceptionFactory<E> efactory) throws E {

    List<String> directories = new ArrayList<>();
    for (String oldoutput : outputPaths) {
      try {
        if (!Files.isDirectory(Paths.get(oldoutput))) {
          workspace.deleteFile(new File(oldoutput));
        } else {
          directories.add(oldoutput);
        }
      } catch (IOException e) {
        throw efactory.exception("Could not delete builder output", e);
      }
    }

    directories.sort((s1, s2) -> {
      if (s1.length() != s2.length()) {
        return s2.length() - s1.length();
      }
      return s2.compareTo(s1);
    });
    for (String oldDirectory : directories) {
      try {
        Path oldDirectoryFile = Paths.get(oldDirectory);
        if (isEmpty(oldDirectoryFile)) {
          workspace.deleteFile(oldDirectoryFile.toFile());
        }
      } catch (IOException e) {
        throw efactory.exception("Could not delete builder output", e);
      }
    }
  }

  private static boolean isEmpty(Path dir) throws IOException {
    try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
      return !ds.iterator().hasNext();
    }
  }

  private Collection<String> getWrittenFilesForDeletion(final BuilderContext builderContext) {
    Collection<String> writtenFiles = builderContext.getWrittenFiles();
    return writtenFiles.stream().filter(f -> !builderContext.wasWhitelistedException(f))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Collection<String> getReadAndTrackExceptions() throws ExpressionEvaluationException {
    ExpressionEvaluator evaluator = new ExpressionEvaluator(Arrays.asList(propertyResolver));
    Collection<String> exceptions = enforcerConfig.getReadAndTrackExceptions(builderId);
    Collection<String> results = new LinkedHashSet<>();
    String basedir = projectBasedir.normalize().toString();

    for (String exception : exceptions) {
      String evaluated = evaluator.evaluate(exception);
      if (!evaluated.startsWith("/")) {
        evaluated = basedir + "/" + evaluated;
      }
      results.add(normalize0(evaluated));
    }

    return results;
  }

  private Path sessionBasedir() {
    // old takari plugin testing harness does not setup session basedir
    return sessionBasedir == null ? projectBasedir : sessionBasedir;
  }

  private Map<String, FileDigest> getExceptionsDigest(Collection<String> paths) {
    Map<String, FileDigest> fileDigests = new LinkedHashMap<>();

    if (paths.isEmpty()) {
      return fileDigests;
    }

    paths.forEach(path -> fileDigests.put(path.toString(), FileDigest.digest(Paths.get(path))));

    return fileDigests;
  }

  private void addCompileSourceRootToProject(CompileSourceRoot csr) {
    ResourceType type = csr.getType();
    if (type.equals(ResourceType.MAIN) && compileSourceRoots != null
        && !compileSourceRoots.contains(csr.getPath())) {
      compileSourceRoots.add(csr.getPath());
    } else if (type.equals(ResourceType.TEST) && testCompileSourceRoots != null
        && !testCompileSourceRoots.contains(csr.getPath())) {
      testCompileSourceRoots.add(csr.getPath());
    }
  }

  private Map<String, Object> propertiesDigest(Collection<String> names) {
    Map<String, Object> digest = new TreeMap<>();
    names.forEach(name -> digest.put(name, SHA1Digester.digest(System.getProperty(name))));
    return digest;
  }

  static Throwable getRootCause(Throwable throwable) {
    Throwable cause;
    while ((cause = throwable.getCause()) != null) {
      throwable = cause;
    }
    return throwable;
  }

  static <E extends Exception> Method getBuilderMethodForGoal(final Class<?> type,
      final String goal, ExceptionFactory<E> efactory) throws E {
    try {
      Method method = streamMethods(type) //
          .filter(m -> isbuilderAnnotationPresentWithGoal(m, goal)) //
          .findFirst() //
          .get();
      if (!method.isAccessible()) {
        method.setAccessible(true);
      }
      return method;
    } catch (NoSuchElementException e) {
      throw efactory.exception(
          String.format("Could not find method with @Builder(name=%s) annotation", goal), e);
    }
  }

  static Stream<Method> streamMethods(Class<?> klass) {
    return (klass == Object.class)
        ? Stream.of()
        : Stream.concat(Arrays.asList(klass.getDeclaredMethods()).stream(),
            streamMethods(klass.getSuperclass()));
  }

  static boolean isbuilderAnnotationPresentWithGoal(Method m, String goal) {
    return m.isAnnotationPresent(Builder.class)
        && m.getAnnotation(Builder.class).name().equals(goal);
  }

  private static String getFormattedViolationsMessage(Set<EnforcerViolation> violations,
      BuilderContext context) {
    StringBuilder msg = new StringBuilder();

    msg.append(String.format("Access to an undeclared resource detected in builder: %s",
        context.toString()));
    msg.append("\nViolated Rules Are:");

    for (EnforcerViolation violation : violations) {
      msg.append("\n   " + violation.getFormattedViolation());
    }

    msg.append("\n");

    String butc_chatter =
        "https://gus.my.salesforce.com/_ui/core/chatter/groups/GroupProfilePage?g=0F9B000000000lg";
    msg.append(String.format("\nSee %s for more information", butc_chatter));

    return msg.toString();
  }

}
