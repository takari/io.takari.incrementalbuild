package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.MessageSeverity;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.ResourceStatus;
import io.takari.incrementalbuild.workspace.Workspace;
import io.takari.incrementalbuild.workspace.Workspace.FileVisitor;
import io.takari.incrementalbuild.workspace.Workspace.Mode;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks build input and output resources and associations among them.
 */
public abstract class AbstractBuildContext {
  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected final Workspace workspace;

  private final File stateFile;

  protected final DefaultBuildContextState state;

  protected final DefaultBuildContextState oldState;

  /**
   * Previous build state does not exist, cannot be read or configuration has changed. When
   * escalated, all input files are considered require processing.
   */
  private final boolean escalated;

  /**
   * Indicates that no further modifications to this build context are allowed.
   */
  private boolean closed;

  /**
   * Resources known to be deleted since previous build. Includes both resources reported as deleted
   * by Workspace and resources explicitly delete through this build context.
   */
  private final Set<File> deletedResources = new HashSet<>();

  /**
   * Resources selected for processing during this build. This includes resources created, changed
   * and deleted through this build context.
   */
  private final Set<Object> processedResources = new HashSet<>();

  protected AbstractBuildContext(BuildContextEnvironment env) {
    this(env.getWorkspace(), env.getStateFile(), env.getParameters(), env.getFinalizer());
  }

  protected AbstractBuildContext(Workspace workspace, File stateFile,
      Map<String, Serializable> configuration, BuildContextFinalizer finalizer) {

    // preconditions
    if (workspace == null) {
      throw new NullPointerException();
    }
    if (configuration == null) {
      throw new NullPointerException();
    }

    this.stateFile = stateFile;
    this.state = DefaultBuildContextState.withConfiguration(configuration);
    this.oldState = DefaultBuildContextState.loadFrom(stateFile);

    final boolean configurationChanged = getConfigurationChanged();
    if (workspace.getMode() == Mode.ESCALATED) {
      this.escalated = true;
      this.workspace = workspace;
    } else if (workspace.getMode() == Mode.SUPPRESSED) {
      this.escalated = false;
      this.workspace = workspace;
    } else if (configurationChanged) {
      this.escalated = true;
      this.workspace = workspace.escalate();
    } else {
      this.escalated = false;
      this.workspace = workspace;
    }

    if (escalated && stateFile != null) {
      if (!stateFile.canRead()) {
        log.info("Previous incremental build state does not exist, performing full build");
      } else {
        log.info("Incremental build configuration change detected, performing full build");
      }
    } else {
      log.info("Performing incremental build");
    }

    if (finalizer != null) {
      finalizer.registerContext(this);
    }
  }

  private boolean getConfigurationChanged() {
    Map<String, Serializable> configuration = state.configuration;
    Map<String, Serializable> oldConfiguration = oldState.configuration;

    if (oldConfiguration.isEmpty()) {
      return true; // no previous state
    }

    Set<String> keys = new TreeSet<String>();
    keys.addAll(configuration.keySet());
    keys.addAll(oldConfiguration.keySet());

    boolean result = false;
    StringBuilder msg = new StringBuilder();

    for (String key : keys) {
      Serializable value = configuration.get(key);
      Serializable oldValue = oldConfiguration.get(key);
      if (!Objects.equals(oldValue, value)) {
        result = true;
        msg.append("\n   ");
        if (value == null) {
          msg.append("REMOVED");
        } else if (oldValue == null) {
          msg.append("ADDED");
        } else {
          msg.append("CHANGED");
        }
        msg.append(' ').append(key);
      }
    }

    if (result) {
      log.debug("Incremental build configuration key changes:{}", msg.toString());
    }

    return result;
  }

  protected boolean isEscalated() {
    return escalated;
  }

  /**
   * Registers matching resources as this build's input set.
   */
  protected Collection<DefaultResourceMetadata<File>> registerInputs(File basedir,
      Collection<String> includes, Collection<String> excludes) throws IOException {
    basedir = normalize(basedir);
    final List<DefaultResourceMetadata<File>> result = new ArrayList<>();
    final FileMatcher matcher = FileMatcher.matcher(basedir, includes, excludes);
    workspace.walk(basedir, new FileVisitor() {
      @Override
      public void visit(File file, long lastModified, long length, Workspace.ResourceStatus status) {
        if (matcher.matches(file)) {
          switch (status) {
            case MODIFIED:
            case NEW:
              result.add(registerNormalizedInput(file, lastModified, length));
              break;
            case REMOVED:
              deletedResources.add(file);
              break;
            default:
              throw new IllegalArgumentException();
          }
        }
      }
    });
    if (workspace.getMode() == Mode.DELTA) {
      // only NEW, MODIFIED and REMOVED resources are reported in DELTA mode
      // need to find any UNMODIFIED
      final FileMatcher absoluteMatcher = FileMatcher.matcher(basedir, includes, excludes);
      for (ResourceHolder<?> holder : oldState.getResources().values()) {
        if (holder instanceof FileState) {
          FileState fileState = (FileState) holder;
          if (!state.isResource(fileState.file) && !deletedResources.contains(fileState.file)
              && absoluteMatcher.matches(fileState.file)) {
            result.add(registerNormalizedInput(fileState.file, fileState.lastModified,
                fileState.length));
          }
        }
      }
    }
    return result;
  }

  protected Collection<DefaultResource<File>> registerAndProcessInputs(File basedir,
      Collection<String> includes, Collection<String> excludes) throws IOException {
    basedir = normalize(basedir);
    final List<DefaultResource<File>> result = new ArrayList<>();
    final FileMatcher matcher = FileMatcher.matcher(basedir, includes, excludes);
    workspace.walk(basedir, new FileVisitor() {
      @Override
      public void visit(File file, long lastModified, long length, Workspace.ResourceStatus status) {
        if (matcher.matches(file)) {
          switch (status) {
            case MODIFIED:
            case NEW:
              DefaultResourceMetadata<File> metadata =
                  registerNormalizedInput(file, lastModified, length);
              if (workspace.getMode() == Mode.DELTA
                  || getResourceStatus(file) != ResourceStatus.UNMODIFIED) {
                result.add(processResource(metadata));
              }
              break;
            case REMOVED:
              deletedResources.add(file);
              break;
            default:
              throw new IllegalArgumentException();
          }
        }
      }
    });
    if (workspace.getMode() == Mode.DELTA) {
      // only NEW, MODIFIED and REMOVED resources are reported in DELTA mode
      // need to find any UNMODIFIED
      final FileMatcher absoluteMatcher = FileMatcher.matcher(basedir, includes, excludes);
      for (ResourceHolder<?> holder : oldState.getResources().values()) {
        if (holder instanceof FileState) {
          FileState fileState = (FileState) holder;
          if (!state.isResource(fileState.file) && !deletedResources.contains(fileState.file)
              && absoluteMatcher.matches(fileState.file)) {
            registerNormalizedInput(fileState.file, fileState.lastModified, fileState.length);
          }
        }
      }
    }
    return result;
  }

  protected static File normalize(File file) {
    if (file == null) {
      throw new IllegalArgumentException();
    }
    try {
      return file.getCanonicalFile();
    } catch (IOException e) {
      return file.getAbsoluteFile();
    }
  }

  protected DefaultResourceMetadata<File> registerNormalizedInput(File resourceFile,
      long lastModified, long length) {
    assertOpen();
    if (!state.isResource(resourceFile)) {
      registerInput(newFileState(resourceFile, lastModified, length));
    }
    return new DefaultResourceMetadata<File>(this, oldState, resourceFile);
  }

  protected DefaultResourceMetadata<File> registerNormalizedOutput(File outputFile) {
    assertOpen();
    if (!state.isResource(outputFile)) {
      state.putResource(outputFile, null); // placeholder
      state.addOutput(outputFile);
    } else {
      if (!state.isOutput(outputFile)) {
        throw new IllegalStateException("Already registered as input " + outputFile);
      }
    }
    return new DefaultResourceMetadata<File>(this, oldState, outputFile);
  }

  private FileState newFileState(File file, long lastModified, long length) {
    if (!workspace.isPresent(file)) {
      throw new IllegalArgumentException("File does not exist or cannot be read " + file);
    }
    return new FileState(file, lastModified, length);
  }

  protected DefaultResourceMetadata<File> registerInput(File inputFile) {
    inputFile = normalize(inputFile);
    return registerNormalizedInput(inputFile, inputFile.lastModified(), inputFile.length());
  }

  /**
   * Adds the resource to this build's resource set. The resource must exist, i.e. it's status must
   * not be REMOVED.
   */
  protected <T extends Serializable> T registerInput(ResourceHolder<T> holder) {
    T resource = holder.getResource();
    ResourceHolder<?> other = state.getResource(resource);
    if (other == null) {
      if (getResourceStatus(holder) == ResourceStatus.REMOVED) {
        throw new IllegalArgumentException("Resource does not exist " + resource);
      }
      state.putResource(resource, holder);
    } else {
      if (state.isOutput(resource)) {
        throw new IllegalStateException("Already registered as output " + resource);
      }
      if (!holder.equals(other)) {
        throw new IllegalArgumentException("Inconsistent resource state " + resource);
      }
      state.putResource(resource, holder);
    }
    return resource;
  }

  /**
   * Returns resource status compared to the previous build.
   */
  protected ResourceStatus getResourceStatus(Object resource) {
    if (deletedResources.contains(resource)) {
      return ResourceStatus.REMOVED;
    }

    ResourceHolder<?> oldResourceState = oldState.getResource(resource);
    if (oldResourceState == null) {
      return ResourceStatus.NEW;
    }

    if (escalated) {
      return ResourceStatus.MODIFIED;
    }

    return getResourceStatus(oldResourceState);
  }

  private ResourceStatus getResourceStatus(ResourceHolder<?> holder) {
    if (holder instanceof FileState) {
      FileState fileState = (FileState) holder;
      switch (workspace.getResourceStatus(fileState.file, fileState.lastModified, fileState.length)) {
        case NEW:
          return ResourceStatus.NEW;
        case MODIFIED:
          return ResourceStatus.MODIFIED;
        case REMOVED:
          return ResourceStatus.REMOVED;
        case UNMODIFIED:
          return ResourceStatus.UNMODIFIED;
      }
      throw new IllegalArgumentException();
    }
    return holder.getStatus();
  }

  protected <T> DefaultResource<T> processResource(DefaultResourceMetadata<T> metadata) {
    final T resource = metadata.getResource();

    if (metadata.context != this || !state.isResource(resource)) {
      throw new IllegalArgumentException();
    }

    if (metadata instanceof DefaultResource) {
      return (DefaultResource<T>) metadata;
    }

    processResource(resource);

    return new DefaultResource<T>(this, state, resource);
  }

  protected void processResource(final Object resource) {
    processedResources.add(resource);

    // reset all metadata associated with the resource during this build
    state.removeResourceAttributes(resource);
    state.removeResourceMessages(resource);
    state.removeResourceOutputs(resource);
  }

  protected void markProcessedResource(Object resource) {
    processedResources.add(resource);
  }

  // simple key/value pairs

  protected <T extends Serializable> Serializable setResourceAttribute(Object resource, String key,
      T value) {
    state.putResourceAttribute(resource, key, value);
    // TODO odd this always returns previous build state. need to think about it
    return oldState.getResourceAttribute(resource, key);
  }

  protected <T extends Serializable> T getResourceAttribute(DefaultBuildContextState state,
      Object resource, String key, Class<T> clazz) {
    Map<String, Serializable> attributes = state.getResourceAttributes(resource);
    return attributes != null ? clazz.cast(attributes.get(key)) : null;
  }

  // persisted messages

  protected void addMessage(Object resource, int line, int column, String message,
      MessageSeverity severity, Throwable cause) {
    // this is likely called as part of builder error handling logic.
    // to make IAE easier to troubleshoot, link cause to the exception thrown
    if (resource == null) {
      throw new IllegalArgumentException(cause);
    }
    if (severity == null) {
      throw new IllegalArgumentException(cause);
    }
    state.addResourceMessage(resource, new Message(line, column, message, severity, cause));
    log(resource, line, column, message, severity, cause);
  }

  protected DefaultOutput processOutput(File outputFile) {
    outputFile = normalize(outputFile);

    registerNormalizedOutput(outputFile);
    processResource(outputFile);

    workspace.processOutput(outputFile);

    return newOutput(outputFile);
  }

  protected OutputStream newOutputStream(DefaultOutput output) throws IOException {
    return workspace.newOutputStream(output.getResource());
  }

  protected <T> DefaultOutput associate(DefaultResource<T> resource, DefaultOutput output) {
    if (resource.context != this) {
      throw new IllegalArgumentException();
    }
    if (output.context != this) {
      throw new IllegalArgumentException();
    }

    assertAssociation(resource, output);

    state.putResourceOutput(resource.getResource(), output.getResource());
    return output;
  }

  protected abstract void assertAssociation(DefaultResource<?> resource, DefaultOutput output);

  protected <T> DefaultOutput associate(DefaultResource<T> resource, File outputFile) {
    return associate(resource, processOutput(outputFile));
  }

  protected Collection<? extends ResourceMetadata<File>> getAssociatedOutputs(
      DefaultBuildContextState state, Object resource) {
    Collection<File> outputFiles = state.getResourceOutputs(resource);
    if (outputFiles == null || outputFiles.isEmpty()) {
      return Collections.emptyList();
    }
    List<ResourceMetadata<File>> outputs = new ArrayList<>();
    for (File outputFile : outputFiles) {
      outputs.add(new DefaultResourceMetadata<File>(this, state, outputFile));
    }
    return outputs;
  }

  public void commit(MessageSinkAdaptor messager) throws IOException {
    if (closed) {
      return;
    }
    this.closed = true;

    // messages recorded during this build
    Map<Object, Collection<Message>> newMessages = new HashMap<>(state.getResourceMessages());

    finalizeContext();

    // assert inputs didn't change
    for (Map.Entry<Object, ResourceHolder<?>> entry : state.getResources().entrySet()) {
      Object resource = entry.getKey();
      ResourceHolder<?> holder = entry.getValue();
      if (!state.isOutput(resource) && holder.getStatus() != ResourceStatus.UNMODIFIED) {
        throw new IllegalStateException("Unexpected input change " + resource);
      }
    }

    // timestamp new outputs
    for (File outputFile : state.getOutputs()) {
      if (state.getResource(outputFile) == null) {
        state.putResource(outputFile,
            newFileState(outputFile, outputFile.lastModified(), outputFile.length()));
      }
    }

    if (stateFile != null) {
      final long start = System.currentTimeMillis();
      try (OutputStream os = workspace.newOutputStream(stateFile)) {
        state.storeTo(os);
      }
      log.debug("Stored incremental build state {} ({} ms)", stateFile, System.currentTimeMillis()
          - start);
    }

    // new messages are logged as soon as they are reported during the build
    // replay old messages so the user can still see them
    Map<Object, Collection<Message>> allMessages = new HashMap<>(state.getResourceMessages());


    if (!allMessages.keySet().equals(newMessages.keySet())) {
      log.info("Replaying recorded messages...");
      for (Map.Entry<Object, Collection<Message>> entry : allMessages.entrySet()) {
        Object resource = entry.getKey();
        if (!newMessages.containsKey(resource)) {
          for (Message message : entry.getValue()) {
            log(resource, message.line, message.column, message.message, message.severity,
                message.cause);
          }
        }
      }
    }

    // processedResources includes resources added, changed and deleted during this build
    // clear all old messages associated with the processed resources during previous builds
    if (messager != null) {
      for (Object resource : processedResources) {
        messager.clear(resource);
      }
      messager.record(allMessages, newMessages);
    }

  }

  protected abstract void finalizeContext() throws IOException;

  protected void log(Object resource, int line, int column, String message,
      MessageSeverity severity, Throwable cause) {
    switch (severity) {
      case ERROR:
        log.error("{}:[{}:{}] {}", resource.toString(), line, column, message, cause);
        break;
      case WARNING:
        log.warn("{}:[{}:{}] {}", resource.toString(), line, column, message, cause);
        break;
      default:
        log.info("{}:[{}:{}] {}", resource.toString(), line, column, message, cause);
        break;
    }
  }

  protected void deleteOutput(File resource) throws IOException {
    if (!oldState.isOutput(resource) && !state.isOutput(resource)) {
      // not an output known to this build context
      throw new IllegalArgumentException();
    }

    workspace.deleteFile(resource);

    deletedResources.add(resource);
    processedResources.add(resource);

    state.removeResource(resource);
    state.removeOutput(resource);

    state.removeResourceAttributes(resource);
    state.removeResourceMessages(resource);
    state.removeResourceOutputs(resource);
  }

  protected void assertOpen() {
    if (closed) {
      throw new IllegalStateException();
    }
  }

  /**
   * Marks skipped build execution. All inputs, outputs and their associated metadata are carried
   * over to the next build as-is. No context modification operations (register* or process) are
   * permitted after this call.
   */
  protected void markSkipExecution() {
    if (!processedResources.isEmpty()) {
      throw new IllegalStateException();
    }

    closed = true;
  }

  protected boolean isProcessedResource(Object resource) {
    return processedResources.contains(resource);
  }

  protected boolean isProcessed() {
    return !processedResources.isEmpty();
  }

  protected boolean isRegisteredResource(Object resource) {
    return state.isResource(resource);
  }

  protected boolean isDeletedResource(Object resource) {
    return deletedResources.contains(resource);
  }

  protected <T> DefaultResourceMetadata<T> newResourceMetadata(DefaultBuildContextState state,
      T resource) {
    return new DefaultResourceMetadata<T>(this, state, resource);
  }

  protected <T> DefaultResource<T> newResource(T resource) {
    return new DefaultResource<T>(this, state, resource);
  }

  protected DefaultOutput newOutput(File resource) {
    return new DefaultOutput(this, state, resource);
  }

  protected void markUptodateOutput(File outputFile) {
    if (!oldState.isOutput(outputFile)) {
      throw new IllegalArgumentException();
    }
    state.putResource(outputFile, oldState.getResource(outputFile));
    state.addOutput(outputFile);
  }

  /** @noreference this is public for for test purposes only */
  public DefaultBuildContextState getOldState() {
    return oldState;
  }

  /** @noreference this is public for for test purposes only */
  public DefaultBuildContextState getState() {
    return state;
  }

  protected DefaultBuildContextState getState(Object source) {
    return isProcessedResource(source) ? this.state : this.oldState;
  }

  protected <V extends Serializable> V getAttribute(Object resource, String key, Class<V> clazz) {
    return getResourceAttribute(getState(resource), resource, key, clazz);
  }

}
