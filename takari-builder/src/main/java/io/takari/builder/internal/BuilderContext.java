package io.takari.builder.internal;

import static io.takari.builder.enforcer.ComposableSecurityManagerPolicy.getContextPolicy;
import static io.takari.builder.enforcer.ComposableSecurityManagerPolicy.registerContextPolicy;
import static io.takari.builder.enforcer.ComposableSecurityManagerPolicy.unregisterContextPolicy;
import static io.takari.builder.internal.pathmatcher.PathNormalizer.normalize0;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import io.takari.builder.Messages;
import io.takari.builder.enforcer.Policy;
import io.takari.builder.enforcer.internal.EnforcerViolation;
import io.takari.builder.enforcer.internal.EnforcerViolationType;
import io.takari.builder.internal.BuilderExecutionState.InprogressStateWriter;
import io.takari.builder.internal.pathmatcher.FileMatcher;
import io.takari.builder.internal.pathmatcher.PathMatcher;
import io.takari.builder.internal.pathmatcher.PathNormalizer;

public class BuilderContext {

  // value is documented in System.setProperty(String, String)
  static final String PROPERTY_WRITE_ACTION = "write";

  // value is documented in SecurityManager.checkPropertyAccess(String)
  static final String PROPERTY_READ_ACTION = "read";

  // value is documented SecurityManager.checkPropertiesAccess()
  static final String PROPERTY_RW_ACTION = "read,write";

  private static final Object KEY_CONTEXT = BuilderContextPolicy.class;

  public void enter() {
    registerContextPolicy(KEY_CONTEXT, new BuilderContextPolicy(this));
  }

  public void leave() {
    BuilderContextPolicy policy = (BuilderContextPolicy) unregisterContextPolicy(KEY_CONTEXT);
    if (policy.getBuilderContext() != this) {
      throw new IllegalStateException();
    }
    policy.inScope.set(false);
  }

  private static BuilderContext getCurrentContext() {
    BuilderContextPolicy policy = (BuilderContextPolicy) getContextPolicy(KEY_CONTEXT);
    if (policy == null) {
      throw new IllegalStateException();
    }
    return policy.getBuilderContext();
  }

  //
  // enforcement
  //

  static class BuilderContextPolicy implements Policy {

    private final BuilderContext ctx;

    public BuilderContextPolicy(BuilderContext ctx) {
      this.ctx = ctx;
    }

    public BuilderContext getBuilderContext() {
      return ctx;
    }

    private final ThreadLocal<Boolean> readPrivileged =
        ThreadLocal.withInitial(() -> Boolean.FALSE);
    private AtomicBoolean inScope = new AtomicBoolean(true);

    @Override
    public void checkWrite(String file) {
      checkScope();
      if (!ctx.checkAndRecordWrite(file)) {
        handleViolation(ctx, EnforcerViolationType.WRITE, file);
      }
    }

    @Override
    public void checkSocketPermission() {
      checkScope();
      if (!ctx.checkSockets()) {
        throw new SecurityException();
      }
    }

    @Override
    public void checkRead(String file) {
      checkScope();
      if (readPrivileged.get() == Boolean.TRUE) {
        return;
      }
      try {
        // TODO evaluate performance overhead of setting/resetting on each invocation
        // this is necessary because builder policy performs secondary filesystem check
        // originally, the filesystem check was performed separately and far less frequently
        // the two checks were collapsed into single policy call for Policy API clarity
        readPrivileged.set(Boolean.TRUE);
        if (!ctx.checkRead(file)) {
          handleViolation(ctx, EnforcerViolationType.READ, normalize0(file));
        }
      } finally {
        readPrivileged.set(Boolean.FALSE);
      }
    }

    @Override
    public void checkPropertyPermission(String action, String name) {
      checkScope();
      if (!ctx.checkAndRecordProperty(action, name)) {
        throw new SecurityException();
      }
    }

    @Override
    public void checkExec(String cmd) {
      checkScope();
      if (!ctx.checkExec(cmd)) {
        handleViolation(ctx, EnforcerViolationType.EXECUTE, cmd);
      }
    }

    //
    // private methods
    //

    private void checkScope() {
      if (!inScope.get()) {
        throw new IllegalStateException("BuilderContext is no longer in scope");
      }
    }

    private void handleViolation(BuilderContext ctx, EnforcerViolationType violationType,
        String path) {
      ctx.addViolation(new EnforcerViolation(violationType, path));
    }

  }

  //
  // Messages
  //

  public static Messages MESSAGES = new Messages() {

    private Messages getCurrentMessages() {
      return getCurrentContext().getMessages();
    }

    @Override
    public void warn(File resource, int line, int column, String message, Throwable cause) {
      getCurrentMessages().warn(resource, line, column, message, cause);
    }

    @Override
    public void info(File resource, int line, int column, String message, Throwable cause) {
      getCurrentMessages().info(resource, line, column, message, cause);
    }

    @Override
    public void error(File resource, int line, int column, String message, Throwable cause) {
      getCurrentMessages().error(resource, line, column, message, cause);
    }

    @Override
    public void warn(Path resource, int line, int column, String message, Throwable cause) {
      getCurrentMessages().warn(resource, line, column, message, cause);
    }

    @Override
    public void info(Path resource, int line, int column, String message, Throwable cause) {
      getCurrentMessages().info(resource, line, column, message, cause);
    }

    @Override
    public void error(Path resource, int line, int column, String message, Throwable cause) {
      getCurrentMessages().error(resource, line, column, message, cause);
    }
  };

  //
  // Construction
  //

  public static class Builder {

    private final Logger log;
    private final String id;
    private final Path sessionBasedir;
    private final PathNormalizer normalizer;
    private final PathMatcher.Builder readMatcherBuilder;
    private final PathMatcher.Builder readAndTrackMatcherBuilder;
    private final PathMatcher.Builder writeMatcherBuilder;
    private final PathMatcher.Builder tempMatcherBuilder;
    private final MessageCollector messages;
    private final Collection<String> execExceptions = new LinkedHashSet<>();
    private boolean networkAccessAllowed;
    private final Collection<String> readExceptions = new LinkedHashSet<>();
    private final Collection<String> writeExceptions = new LinkedHashSet<>();
    private InprogressStateWriter inprogressWriter = BuilderExecutionState.NOOP_INPROGRESSWRITER;
    private final BuilderWorkspace workspace;

    private Builder(Logger log, String id, Path sessionBasedir, MessageCollector messages,
        BuilderWorkspace workspace) {
      this.log = log;
      this.id = id;
      this.sessionBasedir = sessionBasedir;
      this.messages = messages;
      this.normalizer = new PathNormalizer(sessionBasedir);
      this.readMatcherBuilder = PathMatcher.builder(normalizer).excludeRoot();
      this.readAndTrackMatcherBuilder = PathMatcher.builder(normalizer).excludeRoot();
      this.writeMatcherBuilder = PathMatcher.builder(normalizer).excludeRoot();
      this.tempMatcherBuilder = PathMatcher.builder(normalizer).excludeRoot();
      this.workspace = workspace;
    }

    public Builder addInputMatcher(PathMatcher matcher) {
      readMatcherBuilder.addMatcher(matcher);
      return this;
    }

    public Builder addInputFiles(Collection<Path> inputFiles) {
      inputFiles.forEach(f -> readMatcherBuilder.includePath(f.toAbsolutePath().toString()));
      return this;
    }

    public Builder addInputFile(Path file) {
      readMatcherBuilder.includePath(file.toAbsolutePath().toString());
      return this;
    }

    public Builder addInputDirectory(Path directory) {
      readMatcherBuilder.includePrefix(directory.toAbsolutePath().toString());
      return this;
    }

    public Builder addOutputDirectory(Path directory) {
      final String path = directory.toAbsolutePath().toString();

      writeMatcherBuilder.includePrefix(path);
      tempMatcherBuilder.excludePrefix(path);

      return this;
    }

    public Builder addOutputFile(Path file) {
      final String path = file.toAbsolutePath().toString();

      writeMatcherBuilder.includePath(path);
      tempMatcherBuilder.excludePath(path);

      return this;
    }

    public Builder addTemporaryDirectory(Path directory) {
      final String path = directory.toAbsolutePath().toString();

      tempMatcherBuilder.includePrefix(path);
      writeMatcherBuilder.excludePrefix(path);

      return this;
    }

    public Builder addReadExceptions(Collection<String> readExceptions) {
      this.readExceptions.addAll(readExceptions);

      return this;
    }

    public Builder addReadAndTrackExceptions(Collection<String> exceptions) {
      exceptions.forEach(p -> readAndTrackMatcherBuilder.includePath(p));

      return this;
    }

    public Builder addWriteExceptions(Collection<String> writeExceptions) {
      this.writeExceptions.addAll(writeExceptions);

      return this;
    }

    public Builder addExecExceptions(Collection<String> execExceptions) {
      this.execExceptions.addAll(execExceptions);

      return this;
    }

    public Builder setNetworkAccessAllowed(boolean networkAccessAllowed) {
      this.networkAccessAllowed = networkAccessAllowed;

      return this;
    }

    public Builder setInprogressWriter(InprogressStateWriter inprogressWriter) {
      this.inprogressWriter = inprogressWriter;

      return this;
    }

    public BuilderContext build() {
      // tests often create test projects under temp directory
      // therefore explicitly exclude session basedir from temp matcher
      tempMatcherBuilder.excludePrefix(normalize0(sessionBasedir));

      PathMatcher readMatcher = readMatcherBuilder.build();
      PathMatcher writeMatcher = writeMatcherBuilder.build();
      PathMatcher tempMatcher = tempMatcherBuilder.build();
      PathMatcher readAndTrackExceptionsMatcher = readAndTrackMatcherBuilder.build();

      return new BuilderContext(log, normalizer, id, readMatcher, writeMatcher, tempMatcher,
          messages, execExceptions, networkAccessAllowed, readExceptions, writeExceptions,
          readAndTrackExceptionsMatcher, inprogressWriter, workspace);
    }
  }

  public static Builder builder(Logger log, String id, Path sessionBasedir,
      MessageCollector messages, BuilderWorkspace workspace) {
    return new Builder(log, id, sessionBasedir, messages, workspace);
  }

  //
  // Runtime behaviour
  //

  private final Logger log;

  private final String id;

  private final PathNormalizer normalizer;
  private final PathMatcher readMatcher;
  private final PathMatcher writeMatcher;
  private final PathMatcher tempMatcher;
  private final Collection<String> execExceptions;
  private final boolean networkAccessAllowed;
  private final FileMatcher readExceptionsMatcher;
  private final FileMatcher writeExceptionsMatcher;
  private final PathMatcher readAndTrackExceptionsMatcher;

  // mutable context state (below) can be accessed from multiple threads
  // all collections are concurrent-safe to allow reads and writes
  // additionally, access to writes/tempWrites is guarded by writeLock

  private final Set<EnforcerViolation> violations = ConcurrentHashMap.newKeySet();
  private final MessageCollector messages;
  private final BuilderWorkspace workspace;

  /*
   * this lock guarantees that "file created by this builder" and "file exists" checks are in sync
   * with each other as observed by checkRead method. failure to synchronize the two checks results
   * in checkRead false negative if "file exists" check becomes true before
   * "created by this builder".
   * 
   * @see io.takari.builder.internal.BuilderContextTest.testConcurrentReadWriteCheck()
   */
  // igorf: more elaborate ReadWriteLock implementation does not provide measurable performance
  // benefits, going with simpler approach
  private final Object writeLock = new Object();

  private final InprogressStateWriter inprogressWriter;

  // output files written by the builder
  private final Set<String> writes = ConcurrentHashMap.newKeySet();

  // temporary files written by the builder
  private final Set<String> tempWrites = ConcurrentHashMap.newKeySet();

  // system properties read by the builder
  private final Set<String> properties = ConcurrentHashMap.newKeySet();

  private BuilderContext(Logger log, PathNormalizer normalizer, String id, PathMatcher readMatcher,
      PathMatcher writeMatcher, PathMatcher tempMatcher, MessageCollector messages,
      Collection<String> execExceptions, boolean networkAccessAllowed,
      Collection<String> readExceptions, Collection<String> writeExceptions,
      PathMatcher readAndTrackExceptionsMatcher, InprogressStateWriter inprogressWriter,
      BuilderWorkspace workspace) {
    this.log = log;
    this.id = id;
    this.readMatcher = readMatcher;
    this.writeMatcher = writeMatcher;
    this.tempMatcher = tempMatcher;
    this.messages = messages;
    this.normalizer = normalizer;
    this.execExceptions = execExceptions;
    this.networkAccessAllowed = networkAccessAllowed;
    this.inprogressWriter = inprogressWriter;
    this.readExceptionsMatcher = getExceptionsMatcher(readExceptions);
    this.writeExceptionsMatcher = getExceptionsMatcher(writeExceptions);
    this.readAndTrackExceptionsMatcher = readAndTrackExceptionsMatcher;
    this.workspace = workspace;
  }

  private static FileMatcher getExceptionsMatcher(Collection<String> exceptions) {
    return exceptions != null && !exceptions.isEmpty()
        ? FileMatcher.absoluteMatcher(Paths.get("/"), exceptions, null)
        : FileMatcher.absoluteMatcher(Paths.get("/"), null, Arrays.asList("*"));
  }

  @Override
  public String toString() {
    return id;
  }

  public final boolean checkRead(String file) {
    String normalized = normalizer.normalize(file);

    if (readExceptionsMatcher.matches(normalized)
        || readAndTrackExceptionsMatcher.includes(normalized)) {
      return true;
    }

    synchronized (writeLock) {
      if (writes.contains(normalized) || tempWrites.contains(normalized)) {
        return true;
      }

      if (!readMatcher.includes(normalized)) {
        // still allow reads of files that do not exist
        return !workspace.isRegularFile(Paths.get(normalized));
      }
    }
    return true;
  }

  public final boolean checkAndRecordWrite(String file) {
    String normalized = normalizer.normalize(file);

    if (writeExceptionsMatcher.matches(normalized)) {
      return true;
    }

    synchronized (writeLock) {
      if (writes.contains(normalized) || tempWrites.contains(normalized)) {
        return true;
      }

      // Do not allow writes to existing files, unless the existing file is an input (whitelisted in
      // builder-enforcer.config)
      if (workspace.isRegularFile(Paths.get(normalized))
          && !readAndTrackExceptionsMatcher.includes(normalized)) {
        return false;
      }

      if (writeMatcher.includes(normalized)) {
        writes.add(normalized);
        recordInprogressWrite(normalized);
        workspace.processOutput(Paths.get(normalized));
        return true;
      }

      if (tempMatcher.includes(normalized)) {
        tempWrites.add(normalized);
        recordInprogressWrite(normalized);
        return true;
      }
    }

    return false;
  }

  private void recordInprogressWrite(String normalized) {
    if (!readAndTrackExceptionsMatcher.includes(normalized)) {
      inprogressWriter.writePath(normalized);
    }
  }

  public final boolean checkExec(String command) {
    return execExceptions.contains(command);
  }

  public final boolean checkSockets() {
    return networkAccessAllowed;
  }

  public boolean checkAndRecordProperty(String action, String name) {
    // Read properties are considered builder inputs and must be tracked
    if (action.equals(PROPERTY_READ_ACTION) || action.equals(PROPERTY_RW_ACTION)) {
      properties.add(name);
      return true;
    }

    // Allow write of property values.
    // This is necessary because some standard Java library classes, like TimeZone, set system
    // properties, something Builders cannot control.
    // As the downside, this may result unnecessary builder executions during no-change incremental
    // build, triggered by properties not being written by skipped builders (hope this makes sense).
    if (action.equals(PROPERTY_WRITE_ACTION)) {
      return true;
    }

    return false;
  }

  public boolean addViolation(EnforcerViolation violation) {
    boolean added = violations.add(violation);
    if (added) {
      StringBuilder msg = new StringBuilder();
      msg.append(String.format("Access to an undeclared resource detected in builder: %s",
          this.toString()));
      msg.append("\n   " + violation.getFormattedViolation());
      violation.getStackTrace().forEach(s -> msg.append("\n   | ").append(s));
      log.error(msg.toString());
    }
    return added;
  }

  public Set<EnforcerViolation> getViolations() {
    return violations;
  }

  /** returns normalized paths of written files */
  public Collection<String> getWrittenFiles() {
    return writes;
  }

  /** returns normalized paths of written temporary files */
  public Collection<String> getTemporaryFiles() {
    return tempWrites;
  }

  /** returns system properties read by the builder */
  public Collection<String> getReadProperties() {
    return properties;
  }

  public String getId() {
    return id;
  }

  public MessageCollector getMessages() {
    return messages;
  }

  public boolean wasWhitelistedException(String file) {
    return readAndTrackExceptionsMatcher.includes(normalizer.normalize(file));
  }
}
