package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.workspace.MessageSink;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// XXX normalize all File parameters. maybe easier to use URI internally.
// XXX maybe use relative URIs to save heap

public abstract class DefaultBuildContext<BuildFailureException extends Exception>
    implements
      BuildContext {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected final File stateFile;

  private final Workspace workspace;

  private final MessageSink messageSink;

  private final DefaultBuildContextState state;

  private final DefaultBuildContextState oldState;

  /**
   * Previous build state does not exist, cannot be read or configuration has changed. When
   * escalated, all input files are considered require processing.
   */
  private final boolean escalated;

  /**
   * Indicates that no further modifications to this build context are allowed. When context is
   * closed, all register* and process* methods throw {@link IllegalStateException} and
   * {@link #commit()} method does nothing.
   */
  private boolean closed;

  /**
   * Inputs selected for processing during this build.
   */
  private final Map<Object, DefaultInput<?>> processedInputs =
      new HashMap<Object, DefaultInput<?>>();

  /**
   * Outputs registered with this build context during this build.
   */
  private final Set<File> uptodateOutputs = new HashSet<File>();

  /**
   * Outputs processed by this build context during this build.
   */
  private final Map<File, DefaultOutput> processedOutputs = new HashMap<File, DefaultOutput>();

  private final Set<File> deletedInputs = new HashSet<File>();

  private final Set<File> deletedOutputs = new HashSet<File>();

  public DefaultBuildContext(Workspace workspace, MessageSink messageSink, File stateFile,
      Map<String, Serializable> configuration) {
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
    this.messageSink = messageSink;

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
      if (!equals(oldValue, value)) {
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

  private static boolean equals(Serializable a, Serializable b) {
    return a != null ? a.equals(b) : b == null;
  }

  public boolean isEscalated() {
    return escalated;
  }

  private void assertOpen() {
    if (closed) {
      throw new IllegalStateException();
    }
  }

  public <T> DefaultInput<T> processInput(DefaultInputMetadata<T> inputMetadata) {
    if (inputMetadata.context != this) {
      throw new IllegalArgumentException();
    }

    if (inputMetadata instanceof DefaultInput) {
      return (DefaultInput<T>) inputMetadata;
    }

    T inputResource = inputMetadata.getResource();

    @SuppressWarnings("unchecked")
    DefaultInput<T> input = (DefaultInput<T>) processedInputs.get(inputResource);
    if (input == null) {
      input = new DefaultInput<T>(this, state, inputResource);
      processedInputs.put(inputResource, input);

      clearMessages(inputResource);
    }

    return input;
  }

  @Override
  public Iterable<DefaultInput<File>> registerAndProcessInputs(File basedir,
      Collection<String> includes, Collection<String> excludes) throws IOException {
    basedir = normalize(basedir);
    final List<DefaultInput<File>> inputs = new ArrayList<DefaultInput<File>>();
    final FileMatcher matcher = FileMatcher.matcher(basedir, includes, excludes);
    workspace.walk(basedir, new FileVisitor() {
      @Override
      public void visit(File file, long lastModified, long length, Workspace.ResourceStatus status) {
        if (matcher.matches(file)) {
          switch (status) {
            case MODIFIED:
            case NEW: {
              DefaultInput<File> input = getProcessedInput(file);
              if (input == null) {
                DefaultInputMetadata<File> metadata =
                    registerNormalizedInput(file, lastModified, length);
                if (workspace.getMode() == Mode.DELTA
                    || getInputStatus(file, true) != ResourceStatus.UNMODIFIED) {
                  input = metadata.process();
                }
              }
              if (input != null) {
                inputs.add(input);
              }
              break;
            }
            case REMOVED:
              deletedInputs.add(file);
              break;
            default:
              throw new IllegalArgumentException();
          }
        }
      }
    });
    return inputs;
  }

  @SuppressWarnings("unchecked")
  private <I> DefaultInput<I> getProcessedInput(I resource) {
    return (DefaultInput<I>) processedInputs.get(resource);
  }

  // low-level methods

  /**
   * Deletes outputs that were registered during the previous build but not the current build.
   * Usually not called directly, since it is automatically invoked during {@link #commit()}.
   * <p>
   * Result includes DefaultOutput instances removed from the state even if underlying file did not
   * exist.
   * <p>
   * If {@code eager == false}, preserves outputs associated with existing inputs during the
   * previous build and outputs that do not have associated inputs. This is useful if generator
   * needs access to old output files during multi-round build. For example, java incremental
   * compiler needs to compare old and new version of class files to determine if changes need to be
   * propagated.
   * 
   * @return deleted outputs
   * 
   * @throws IOException if an orphaned output file cannot be deleted.
   */
  public Iterable<DefaultOutputMetadata> deleteStaleOutputs(boolean eager) throws IOException {
    List<DefaultOutputMetadata> deleted = new ArrayList<DefaultOutputMetadata>();

    oldOutputs: for (File outputFile : oldState.outputs.keySet()) {
      // keep if output file was processed or marked as up-to-date during this build
      if (processedOutputs.containsKey(outputFile) || uptodateOutputs.contains(outputFile)) {
        continue oldOutputs;
      }

      Collection<Object> associatedInputs = oldState.outputInputs.get(outputFile);
      if (associatedInputs != null) {
        for (Object inputResource : associatedInputs) {

          // input is registered and not processed, not orphaned
          if (isRegistered(inputResource) && !processedInputs.containsKey(inputResource)) {
            continue oldOutputs;
          }

          final DefaultInput<?> input = processedInputs.get(inputResource);
          // if not eager, let the caller deal with the outputs
          if (input != null && (!eager || isAssociatedOutput(input, outputFile))) {
            // the oldOutput is associated with an input, not orphaned
            continue oldOutputs;
          }
        }
      } else if (!eager) {
        // outputs without inputs maybe recreated, retained during non-eager delete
        continue oldOutputs;
      }

      // don't double-delete already deleted outputs
      if (!deletedOutputs.add(outputFile)) {
        continue oldOutputs;
      }

      deleteStaleOutput(outputFile);

      deleted.add(new DefaultOutputMetadata(this, oldState, outputFile));
    }
    return deleted;
  }

  public Iterable<DefaultOutputMetadata> deleteStaleOutputs(DefaultInputMetadata<?> input)
      throws IOException {
    List<DefaultOutputMetadata> deleted = new ArrayList<DefaultOutputMetadata>();

    Object inputResource = input.getResource();
    boolean registered = isRegistered(inputResource);

    if (registered && !processedInputs.containsKey(inputResource)) {
      // input is registered and not processed, all associated outputs should be carried over
      return deleted;
    }

    Collection<File> oldAssociatedOutputs = oldState.inputOutputs.get(inputResource);

    if (oldAssociatedOutputs == null || oldAssociatedOutputs.isEmpty()) {
      // input didn't have associated outputs in the previous build, nothing to delete
      return deleted;
    }

    Collection<File> associatedOutputs = state.inputOutputs.get(inputResource);
    for (File oldOutputFile : oldAssociatedOutputs) {
      if (associatedOutputs == null || !associatedOutputs.contains(oldOutputFile)) {
        deleteStaleOutput(oldOutputFile);
        deleted.add(new DefaultOutputMetadata(this, oldState, oldOutputFile));
      }
    }

    return deleted;
  }

  /**
   * Returns {@code true} if inputResource is considered part of inputs set of the current build,
   * i.e. it was registered during this build.
   * <p>
   * For Workspace.Mode.DELTA this also includes inputResources that were registered in the previous
   * build and were not explicitly deleted in this build.
   */
  private boolean isRegistered(Object inputResource) {
    if (state.inputs.containsKey(inputResource)) {
      return true;
    }
    if (workspace.getMode() == Mode.DELTA || workspace.getMode() == Mode.SUPPRESSED) {
      return oldState.inputs.containsKey(inputResource) && !deletedInputs.contains(inputResource);
    }
    return false;
  }

  protected void deleteStaleOutput(File outputFile) throws IOException {
    workspace.deleteFile(outputFile);
  }

  private static <K, V> void put(Map<K, Collection<V>> multimap, K key, V value) {
    Collection<V> values = multimap.get(key);
    if (values == null) {
      values = new LinkedHashSet<V>();
      multimap.put(key, values);
    }
    values.add(value);
  }

  private static <K, V> void putAll(Map<K, Collection<V>> multimap, K key, Collection<V> value) {
    Collection<V> values = multimap.get(key);
    if (values == null) {
      values = new LinkedHashSet<V>();
      multimap.put(key, values);
    }
    values.addAll(value);
  }

  @Override
  public DefaultOutput processOutput(File outputFile) {
    assertOpen();

    outputFile = normalize(outputFile);

    DefaultOutput output = processedOutputs.get(outputFile);
    if (output == null) {
      output = new DefaultOutput(this, state, outputFile);
      processedOutputs.put(outputFile, output);

      workspace.processOutput(outputFile);
      clearMessages(output);
    }

    return output;
  }

  /**
   * Marks all outputs processed during the previous build as up-to-date, in other words, the
   * outputs and their associated metadata are carried over to the next build as-is. This method is
   * only allowed when {@link #isProcessingRequired()} returns {@code false}. No context
   * modification operations (register* or process) are permitted after this call.
   * <p>
   * This is useful when this build context is used to track both inputs and outputs but not
   * association between the two. Without input/output association information the build context is
   * not able to determine what outputs are stale/orphaned and what outputs are still relevant.
   */
  public void markOutputsAsUptodate() {
    if (isProcessingRequired() || !oldState.inputOutputs.isEmpty()) {
      throw new IllegalStateException();
    }

    closed = true;
  }

  /**
   * Marks skipped build execution. All inputs, outputs and their associated metadata are carried
   * over to the next build as-is. No context modification operations (register* or process) are
   * permitted after this call.
   */
  public void markSkipExecution() {
    if (isModified()) {
      throw new IllegalStateException();
    }

    closed = true;
  }

  public void markOutputAsUptodate(File outputFile) {
    uptodateOutputs.add(outputFile);
  }

  public DefaultInput<File> processIncludedInput(File inputFile) {
    inputFile = normalize(inputFile);

    if (state.includedInputs.containsKey(inputFile)) {
      return new DefaultInput<File>(this, state, inputFile);
    }

    File file = registerInput(state.includedInputs, newFileState(inputFile));

    return new DefaultInput<File>(this, state, file);
  }

  public ResourceStatus getInputStatus(Object inputResource, boolean associated) {
    if (!isRegistered(inputResource)) {
      if (oldState.inputs.containsKey(inputResource)) {
        return ResourceStatus.REMOVED;
      }
      throw new IllegalArgumentException("Unregistered input file " + inputResource);
    }

    ResourceHolder<?> oldInputState = oldState.inputs.get(inputResource);
    if (oldInputState == null) {
      return ResourceStatus.NEW;
    }

    if (escalated) {
      return ResourceStatus.MODIFIED;
    }

    ResourceStatus status = getResourceStatus(oldInputState);

    if (status != ResourceStatus.UNMODIFIED) {
      return status;
    }

    if (associated) {
      Collection<Object> includedInputs = oldState.inputIncludedInputs.get(inputResource);
      if (includedInputs != null) {
        for (Object includedInput : includedInputs) {
          ResourceHolder<?> includedInputState = oldState.includedInputs.get(includedInput);
          if (getResourceStatus(includedInputState) != ResourceStatus.UNMODIFIED) {
            return ResourceStatus.MODIFIED;
          }
        }
      }

      Collection<File> outputFiles = oldState.inputOutputs.get(inputResource);
      if (outputFiles != null) {
        for (File outputFile : outputFiles) {
          ResourceHolder<File> outputState = oldState.outputs.get(outputFile);
          if (getResourceStatus(outputState) != ResourceStatus.UNMODIFIED) {
            return ResourceStatus.MODIFIED;
          }
        }
      }
    }

    return ResourceStatus.UNMODIFIED;
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

  public ResourceStatus getOutputStatus(File outputFile) {
    ResourceHolder<File> oldOutputState = oldState.outputs.get(outputFile);

    if (oldOutputState == null) {
      return ResourceStatus.NEW;
    }

    ResourceStatus status = getResourceStatus(oldOutputState);

    if (escalated && status == ResourceStatus.UNMODIFIED) {
      status = ResourceStatus.MODIFIED;
    }

    return status;
  }

  @Override
  public DefaultInputMetadata<File> registerInput(File inputFile) {
    inputFile = normalize(inputFile);
    return registerNormalizedInput(inputFile, inputFile.lastModified(), inputFile.length());
  }

  private DefaultInputMetadata<File> registerNormalizedInput(File inputFile, long lastModified,
      long length) {

    if (state.inputs.containsKey(inputFile)) {
      // performance shortcut, avoids IO during new FileState
      return new DefaultInputMetadata<File>(this, oldState, inputFile);
    }

    return registerInput(newFileState(inputFile, lastModified, length));
  }

  public <T extends Serializable> DefaultInputMetadata<T> registerInput(ResourceHolder<T> holder) {
    T resource = registerInput(state.inputs, holder);

    // this returns different instance each invocation. This should not be a problem because
    // each instance is a stateless flyweight.

    return new DefaultInputMetadata<T>(this, oldState, resource);
  }

  private <T extends Serializable> T registerInput(Map<Object, ResourceHolder<?>> inputs,
      ResourceHolder<T> holder) {
    assertOpen();

    T resource = holder.getResource();

    ResourceHolder<?> other = inputs.get(resource);

    if (other == null) {
      if (getResourceStatus(holder) == ResourceStatus.REMOVED) {
        throw new IllegalArgumentException("Input does not exist " + resource);
      }

      inputs.put(resource, holder);
    } else {
      if (!holder.equals(other)) {
        throw new IllegalArgumentException("Inconsistent input state " + resource);
      }
    }

    return resource;
  }

  public Iterable<DefaultInputMetadata<File>> registerInputs(Iterable<File> inputs) {
    List<DefaultInputMetadata<File>> result = new ArrayList<>();
    for (File inputFile : inputs) {
      result.add(registerInput(inputFile));
    }
    return result;
  }

  @Override
  public Iterable<DefaultInputMetadata<File>> registerInputs(File basedir,
      Collection<String> includes, Collection<String> excludes) throws IOException {
    basedir = normalize(basedir);
    final List<DefaultInputMetadata<File>> result = new ArrayList<>();
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
              deletedInputs.add(file);
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
      for (Object resource : oldState.inputs.keySet()) {
        if (resource instanceof File) {
          File file = (File) resource;
          if (!state.inputs.containsKey(file) && !deletedInputs.contains(file)
              && absoluteMatcher.matches(file)) {
            // TODO carry-over FileState
            result.add(registerInput(file));
          }
        }
      }
    }
    return result;
  }

  @Override
  public Iterable<DefaultInputMetadata<File>> getRegisteredInputs() {
    return getRegisteredInputs(File.class);
  }

  public <T> Iterable<DefaultInputMetadata<T>> getRegisteredInputs(Class<T> clazz) {
    Set<DefaultInputMetadata<T>> result = new LinkedHashSet<DefaultInputMetadata<T>>();
    for (Object inputResource : state.inputs.keySet()) {
      addRegisteredInput(result, clazz, inputResource);
    }
    for (Object inputResource : oldState.inputs.keySet()) {
      if (!state.inputs.containsKey(inputResource)) {
        addRegisteredInput(result, clazz, inputResource);
      }
    }
    return result;
  }

  private <T> void addRegisteredInput(Set<DefaultInputMetadata<T>> result, Class<T> clazz,
      Object inputResource) {
    if (clazz.isInstance(inputResource)) {
      DefaultInputMetadata<T> input = getProcessedInput(clazz.cast(inputResource));
      if (input == null) {
        input = new DefaultInputMetadata<T>(this, state, clazz.cast(inputResource));
      }
      result.add(input);
    }
  }

  public <T> Set<DefaultInputMetadata<T>> getRemovedInputs(Class<T> clazz) {
    Set<DefaultInputMetadata<T>> result = new LinkedHashSet<DefaultInputMetadata<T>>();
    addRemovedInputs(result, clazz);
    return result;
  }

  private <T> void addRemovedInputs(Set<DefaultInputMetadata<T>> result, Class<T> clazz) {
    for (Object inputResource : oldState.inputs.keySet()) {
      if (!isRegistered(inputResource) && clazz.isInstance(inputResource)) {
        // removed
        result.add(new DefaultInputMetadata<T>(this, oldState, clazz.cast(inputResource)));
      }
    }
  }


  @Override
  public Iterable<DefaultOutputMetadata> getProcessedOutputs() {
    Set<DefaultOutputMetadata> result = new LinkedHashSet<DefaultOutputMetadata>();
    for (DefaultOutput output : processedOutputs.values()) {
      result.add(output);
    }
    for (File outputFile : oldState.outputs.keySet()) {
      if (!processedOutputs.containsKey(outputFile)) {
        Collection<Object> associatedInputs = oldState.outputInputs.get(outputFile);
        if (associatedInputs != null) {
          for (Object inputResource : associatedInputs) {
            if (isRegistered(inputResource) && !processedInputs.containsKey(inputResource)) {
              result.add(new DefaultOutputMetadata(this, oldState, outputFile));
              break;
            }
          }
        } else {
          result.add(new DefaultOutputMetadata(this, oldState, outputFile));
        }
      }
    }
    return result;
  }

  public DefaultOutputMetadata registerOutput(File outputFile) {
    outputFile = normalize(outputFile);
    DefaultOutputMetadata output = processedOutputs.get(outputFile);
    if (output == null) {
      output = new DefaultOutputMetadata(this, oldState, outputFile);
    }
    return output;
  }

  private File normalize(File file) {
    if (file == null) {
      throw new IllegalArgumentException();
    }
    try {
      return file.getCanonicalFile();
    } catch (IOException e) {
      log.debug("Could not normalize file {}", file, e);
      return file.getAbsoluteFile();
    }
  }

  // association management

  public void associate(DefaultInputMetadata<?> input, DefaultOutput output) {
    Object inputResource = input.getResource();
    File outputFile = output.getResource();
    if (!processedInputs.containsKey(inputResource)) {
      if (!contains(oldState.inputOutputs.get(inputResource), outputFile)
          || !contains(oldState.outputInputs.get(outputFile), inputResource)) {
        throw new IllegalArgumentException();
      }
    }
    put(state.inputOutputs, inputResource, outputFile);
    put(state.outputInputs, outputFile, inputResource);
  }

  private static <T> boolean contains(Collection<T> collection, T member) {
    return collection != null ? collection.contains(member) : false;
  }

  private boolean isAssociatedOutput(DefaultInput<?> input, File outputFile) {
    Collection<File> outputs = state.inputOutputs.get(input.getResource());
    return outputs != null && outputs.contains(outputFile);
  }

  <I> Iterable<DefaultInputMetadata<I>> getAssociatedInputs(DefaultBuildContextState state,
      File outputFile, Class<I> clazz) {
    Collection<Object> inputFiles = state.outputInputs.get(outputFile);
    if (inputFiles == null || inputFiles.isEmpty()) {
      return Collections.emptyList();
    }
    List<DefaultInputMetadata<I>> inputs = new ArrayList<DefaultInputMetadata<I>>();
    for (Object inputFile : inputFiles) {
      if (clazz.isAssignableFrom(clazz)) {
        inputs.add(new DefaultInputMetadata<I>(this, state, clazz.cast(inputFile)));
      }
    }
    return inputs;
  }

  OutputStream newOutputStream(DefaultOutput output) throws IOException {
    return workspace.newOutputStream(output.getResource());
  }

  public Iterable<DefaultOutput> getAssociatedOutputs(File inputFile) {
    Collection<File> outputFiles = state.inputOutputs.get(inputFile);
    if (outputFiles == null || outputFiles.isEmpty()) {
      return Collections.emptyList();
    }
    List<DefaultOutput> outputs = new ArrayList<DefaultOutput>();
    for (File outputFile : outputFiles) {
      outputs.add(this.processedOutputs.get(outputFile));
    }
    return outputs;
  }

  Iterable<DefaultOutputMetadata> getAssociatedOutputs(DefaultBuildContextState state,
      Object inputResource) {
    Collection<File> outputFiles = state.inputOutputs.get(inputResource);
    if (outputFiles == null || outputFiles.isEmpty()) {
      return Collections.emptyList();
    }
    List<DefaultOutputMetadata> outputs = new ArrayList<DefaultOutputMetadata>();
    for (File outputFile : outputFiles) {
      outputs.add(new DefaultOutputMetadata(this, state, outputFile));
    }
    return outputs;
  }

  public void associateIncludedInput(DefaultInput<?> input, DefaultInput<File> included) {
    put(state.inputIncludedInputs, input.getResource(), included.getResource());
  }

  // provided/required capability matching

  void addRequirement(DefaultInput<?> input, String qualifier, String localName) {
    addRequirement(input, new QualifiedName(qualifier, localName));
  }

  Collection<String> getRequirements(DefaultInputMetadata<?> input, DefaultBuildContextState state,
      String qualifier) {
    Set<String> requirements = new HashSet<String>();
    Collection<QualifiedName> inputRequirements = state.inputRequirements.get(input.getResource());
    if (inputRequirements != null) {
      for (QualifiedName requirement : inputRequirements) {
        if (qualifier.equals(requirement.getQualifier())) {
          requirements.add(requirement.getLocalName());
        }
      }
    }
    return requirements;
  }

  private void addRequirement(DefaultInput<?> input, QualifiedName requirement) {
    addInputRequirement(input.getResource(), requirement);
  }

  private void addInputRequirement(Object inputResource, QualifiedName requirement) {
    put(state.requirementInputs, requirement, inputResource);
    put(state.inputRequirements, inputResource, requirement);
  }

  public void addCapability(DefaultOutput output, String qualifier, String localName) {
    put(state.outputCapabilities, output.getResource(), new QualifiedName(qualifier, localName));
  }

  public Collection<String> getOutputCapabilities(File outputFile, String qualifier) {
    Collection<QualifiedName> capabilities = state.outputCapabilities.get(outputFile);
    if (capabilities == null) {
      return Collections.emptyList();
    }
    Set<String> result = new LinkedHashSet<String>();
    for (QualifiedName capability : capabilities) {
      if (qualifier.equals(capability.getQualifier())) {
        result.add(capability.getLocalName());
      }
    }
    return result;
  }

  /**
   * Returns {@code Input}s with specified requirement. Inputs from the old state are automatically
   * registered for processing.
   */
  public Iterable<DefaultInputMetadata<File>> getDependentInputs(String qualifier, String localName) {
    Map<Object, DefaultInputMetadata<File>> result =
        new LinkedHashMap<Object, DefaultInputMetadata<File>>();

    QualifiedName requirement = new QualifiedName(qualifier, localName);

    Collection<Object> inputResources = state.requirementInputs.get(requirement);
    if (inputResources != null) {
      for (Object inputResource : inputResources) {
        if (inputResource instanceof File) {
          result.put(inputResource, getProcessedInput((File) inputResource));
        }
      }
    }

    Collection<Object> oldInputResources = oldState.requirementInputs.get(requirement);
    if (oldInputResources != null) {
      for (Object inputResource : oldInputResources) {
        ResourceHolder<?> oldInputState = oldState.inputs.get(inputResource);
        if (inputResource instanceof File) {
          if (!result.containsKey(inputResource)
              && getResourceStatus(oldInputState) != ResourceStatus.REMOVED) {
            result.put(inputResource, registerInput((File) inputResource));
          }
        }
      }
    }

    return result.values();
  }

  // simple key/value pairs

  public <T extends Serializable> Serializable setResourceAttribute(Object resource, String key,
      T value) {
    Map<String, Serializable> attributes = state.resourceAttributes.get(resource);
    if (attributes == null) {
      attributes = new LinkedHashMap<String, Serializable>();
      state.resourceAttributes.put(resource, attributes);
    }
    attributes.put(key, value);
    Map<String, Serializable> oldAttributes = oldState.resourceAttributes.get(resource);
    return oldAttributes != null ? (Serializable) oldAttributes.get(key) : null;
  }

  public <T extends Serializable> T getResourceAttribute(Object resource, String key,
      boolean previous, Class<T> clazz) {
    Map<String, Serializable> attributes =
        (previous ? oldState : state).resourceAttributes.get(resource);
    return attributes != null ? clazz.cast(attributes.get(key)) : null;
  }

  // messages

  public void addMessage(Object resource, int line, int column, String message, Severity severity,
      Throwable cause) {
    // this is likely called as part of builder error handling logic.
    // to make IAE easier to troubleshoot, link cause to the exception thrown
    if (resource == null) {
      throw new IllegalArgumentException(cause);
    }
    if (severity == null) {
      throw new IllegalArgumentException(cause);
    }
    put(state.resourceMessages, resource, new Message(line, column, message, severity, cause));
    log(resource, line, column, message, severity, cause);
  }

  protected void log(Object resource, int line, int column, String message, Severity severity,
      Throwable cause) {
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


  Collection<Message> getMessages(Object resource) {
    if (processedInputs.containsKey(resource) || processedOutputs.containsKey(resource)) {
      return state.resourceMessages.get(resource);
    }
    return oldState.resourceMessages.get(resource);
  }

  public void commit() throws BuildFailureException, IOException {
    if (closed) {
      return;
    }
    this.closed = true;

    deleteStaleOutputs(true);

    // carry over relevant parts of the old state

    Map<Object, Collection<Message>> newMessages = new HashMap<>(state.resourceMessages);
    Map<Object, Collection<Message>> recordedMessages = new HashMap<>();

    for (Object inputResource : oldState.inputs.keySet()) {
      if (!isRegistered(inputResource)) {
        clearMessages(inputResource);
        continue;
      }
      if (!state.inputs.containsKey(inputResource)) {
        // this is possible with delta workspaces
        state.inputs.put(inputResource, oldState.inputs.get(inputResource));
      }
      if (!processedInputs.containsKey(inputResource)) {
        // copy associated outputs
        Collection<File> associatedOutputs = oldState.inputOutputs.get(inputResource);
        if (associatedOutputs != null) {
          for (File outputFile : associatedOutputs) {
            if (!processedOutputs.containsKey(outputFile)) {
              carryOverOutput(inputResource, outputFile);
              carryOverMessages(outputFile, recordedMessages);
            }
          }
        }

        // copy associated included inputs
        Collection<Object> includedInputs = oldState.inputIncludedInputs.get(inputResource);
        if (includedInputs != null) {
          state.inputIncludedInputs.put(inputResource, new LinkedHashSet<Object>(includedInputs));

          for (Object includedInput : includedInputs) {
            ResourceHolder<?> oldHolder = oldState.includedInputs.get(includedInput);
            state.includedInputs.put(oldHolder.getResource(), oldHolder);
          }
        }

        // copy requirements
        Collection<QualifiedName> requirements = oldState.inputRequirements.get(inputResource);
        if (requirements != null) {
          for (QualifiedName requirement : requirements) {
            addInputRequirement(inputResource, requirement);
          }
        }

        // copy messages
        carryOverMessages(inputResource, recordedMessages);

        // copy attributes
        Map<String, Serializable> attributes = oldState.resourceAttributes.get(inputResource);
        if (attributes != null) {
          state.resourceAttributes.put(inputResource, attributes);
        }
      }
    }

    for (ResourceHolder<?> resource : state.inputs.values()) {
      if (getResourceStatus(resource) != ResourceStatus.UNMODIFIED) {
        throw new IllegalStateException("Unexpected input change " + resource.getResource());
      }
    }

    // carry over up-to-date output files
    for (File outputFile : oldState.outputs.keySet()) {
      if (uptodateOutputs.contains(outputFile)) {
        carryOverOutput(outputFile);
        carryOverMessages(outputFile, recordedMessages);
      }
    }

    // timestamp processed output files
    for (File outputFile : processedOutputs.keySet()) {
      if (state.outputs.get(outputFile) == null) {
        state.outputs.put(outputFile, newFileState(outputFile));
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

    if (!recordedMessages.isEmpty()) {
      log.info("Replaying recorded messages...");
      for (Map.Entry<Object, Collection<Message>> entry : state.resourceMessages.entrySet()) {
        Object resource = entry.getKey();
        for (Message message : entry.getValue()) {
          log(resource, message.line, message.column, message.message, message.severity,
              message.cause);
        }
      }
    }

    if (messageSink != null) {
      // let message sink record all new messages
      for (Map.Entry<Object, Collection<Message>> entry : newMessages.entrySet()) {
        Object resource = entry.getKey();
        for (Message message : entry.getValue()) {
          messageSink.message(resource, message.line, message.column, message.message,
              toMessageSinkSeverity(message.severity), message.cause);
        }
      }
    } else {
      // without messageSink, have to raise exception if there were errors
      int errorCount = 0;
      StringBuilder errors = new StringBuilder();
      for (Map.Entry<Object, Collection<Message>> entry : state.resourceMessages.entrySet()) {
        Object resource = entry.getKey();
        for (Message message : entry.getValue()) {
          if (message.severity == Severity.ERROR) {
            errorCount++;
            errors.append(String.format("%s:[%d:%d] %s\n", resource.toString(), message.line,
                message.column, message.message));
          }
        }
      }
      if (errorCount > 0) {
        throw newBuildFailureException(errorCount + " error(s) encountered:\n" + errors.toString());
      }
    }
  }

  private void clearMessages(Object resource) {
    if (messageSink != null) {
      messageSink.clearMessages(resource);
    }
  }

  private MessageSink.Severity toMessageSinkSeverity(Severity severity) {
    switch (severity) {
      case ERROR:
        return MessageSink.Severity.ERROR;
      case WARNING:
        return MessageSink.Severity.WARNING;
      case INFO:
        return MessageSink.Severity.INFO;
      default:
        throw new IllegalArgumentException();
    }
  }

  private FileState newFileState(File file) {
    return newFileState(file, file.lastModified(), file.length());
  }

  private FileState newFileState(File file, long lastModified, long length) {
    if (!workspace.isPresent(file)) {
      throw new IllegalArgumentException("File does not exist or cannot be read " + file);
    }
    return new FileState(file, lastModified, length);
  }

  private void carryOverMessages(Object resource, Map<Object, Collection<Message>> recordedMessages) {
    Collection<Message> messages = oldState.resourceMessages.get(resource);
    if (messages != null && !messages.isEmpty()) {
      state.resourceMessages.put(resource, new ArrayList<Message>(messages));

      putAll(recordedMessages, resource, messages);
    }
  }

  protected void carryOverOutput(Object inputResource, File outputFile) {
    carryOverOutput(outputFile);

    put(state.inputOutputs, inputResource, outputFile);
    put(state.outputInputs, outputFile, inputResource);
  }

  protected void carryOverOutput(File outputFile) {
    state.outputs.put(outputFile, oldState.outputs.get(outputFile));

    Collection<QualifiedName> capabilities = oldState.outputCapabilities.get(outputFile);
    if (capabilities != null) {
      state.outputCapabilities.put(outputFile, new LinkedHashSet<QualifiedName>(capabilities));
    }

    Map<String, Serializable> attributes = oldState.resourceAttributes.get(outputFile);
    if (attributes != null) {
      state.resourceAttributes.put(outputFile, attributes);
    }
  }

  public boolean isProcessingRequired() {
    if (escalated || isModified()) {
      return true;
    }
    for (Object inputResource : state.inputs.keySet()) {
      ResourceHolder<?> oldInputState = oldState.inputs.get(inputResource);
      if (oldInputState == null || getResourceStatus(oldInputState) != ResourceStatus.UNMODIFIED) {
        return true;
      }
    }
    for (Object oldInputResource : oldState.inputs.keySet()) {
      if (!isRegistered(oldInputResource)) {
        return true;
      }
    }
    for (ResourceHolder<?> oldIncludedInputState : oldState.includedInputs.values()) {
      if (getResourceStatus(oldIncludedInputState) != ResourceStatus.UNMODIFIED) {
        return true;
      }
    }
    for (ResourceHolder<File> oldOutputState : oldState.outputs.values()) {
      if (getResourceStatus(oldOutputState) != ResourceStatus.UNMODIFIED) {
        return true;
      }
    }
    return false;
  }

  private boolean isModified() {
    return !processedInputs.isEmpty() || !processedOutputs.isEmpty() || !deletedOutputs.isEmpty()
        || !deletedInputs.isEmpty();
  }

  protected abstract BuildFailureException newBuildFailureException(String message);
}
