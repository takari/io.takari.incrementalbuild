package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// XXX normalize all File parameters. maybe easier to use URI internally.
// XXX maybe use relative URIs to save heap

public abstract class DefaultBuildContext<BuildFailureException extends Exception>
    implements
      BuildContext {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final File stateFile;

  private final DefaultBuildContextState state;

  private final DefaultBuildContextState oldState;

  /**
   * Previous build state does not exist, cannot be read or configuration has changed. When
   * escalated, all input files are considered require processing.
   */
  private final boolean escalated;

  /**
   * All inputs selected for processing during this build.
   */
  private final Map<Object, DefaultInput<?>> processedInputs =
      new HashMap<Object, DefaultInput<?>>();

  /**
   * Outputs registered with this build context during this build.
   */
  private final Map<File, DefaultOutput> processedOutputs = new HashMap<File, DefaultOutput>();

  private final Set<File> deletedOutputs = new HashSet<File>();

  /**
   * Number of error messages
   */
  private final AtomicInteger errorCount = new AtomicInteger();

  public DefaultBuildContext(File stateFile, Map<String, byte[]> configuration) {
    // preconditions
    if (stateFile == null) {
      throw new NullPointerException();
    }
    if (configuration == null) {
      throw new NullPointerException();
    }

    this.stateFile = stateFile;
    this.state = new DefaultBuildContextState(configuration);
    this.oldState = loadState(stateFile);

    this.escalated = getEscalated(configuration);
  }

  private boolean getEscalated(Map<String, byte[]> configuration) {
    Map<String, byte[]> oldConfiguration = oldState.configuration;

    if (!oldConfiguration.keySet().equals(configuration.keySet())) {
      log.debug("Inconsistent configuration keys, old={}, new={}", oldConfiguration.keySet(),
          configuration.keySet());
      return true;
    }

    Set<String> keys = new TreeSet<String>();
    for (String key : oldConfiguration.keySet()) {
      if (!Arrays.equals(oldConfiguration.get(key), configuration.get(key))) {
        keys.add(key);
      }
    }

    if (!keys.isEmpty()) {
      log.debug("Configuration changed, changed keys={}", keys);
      return true;
    }

    // XXX need a way to debug detailed configuration of changed keys

    return false;
  }

  private DefaultBuildContextState loadState(File stateFile) {
    // TODO verify stateFile location has not changed since last build
    // TODO wrap collections in corresponding immutable collections
    try {
      ObjectInputStream is =
          new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(
              stateFile))));
      try {
        return (DefaultBuildContextState) is.readObject();
      } finally {
        try {
          is.close();
        } catch (IOException e) {
          // ignore secondary exceptions
        }
      }
    } catch (FileNotFoundException e) {
      // this is expected, ignore
    } catch (Exception e) {
      log.debug("Could not read build state file {}", stateFile, e);
    }
    return DefaultBuildContextState.emptyState();
  }

  private void storeState() throws IOException {
    // timestamp processed output files
    for (File outputFile : processedOutputs.keySet()) {
      if (state.outputs.get(outputFile) == null) {
        state.outputs.put(outputFile, new FileState(outputFile));
      }
    }

    File parent = stateFile.getParentFile();
    if (!parent.isDirectory() && !parent.mkdirs()) {
      throw new IOException("Could not create direcotyr " + parent);
    }

    ObjectOutputStream os =
        new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(
            stateFile))));
    try {
      os.writeObject(state);
    } finally {
      try {
        os.close();
      } catch (IOException e) {
        // ignore secondary exception
      }
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
    }

    return input;
  }

  @Override
  public Iterable<DefaultInput<File>> registerAndProcessInputs(Iterable<File> inputFiles) {
    List<DefaultInput<File>> inputs = new ArrayList<DefaultInput<File>>();
    for (DefaultInputMetadata<File> metadata : registerInputs(inputFiles)) {
      DefaultInput<File> input = getProcessedInput(metadata.getResource());
      if (input == null) {
        if (getInputStatus(metadata.getResource(), true) != ResourceStatus.UNMODIFIED) {
          input = processInput(metadata);
        }
      }
      if (input != null) {
        inputs.add(input);
      }
    }
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
   * previous build. This is useful if generator needs access to old output files during multi-round
   * build. For example, java incremental compiler needs to compare old and new version of class
   * files to determine if changes need to be propagated.
   * 
   * @return deleted outputs
   * 
   * @throws IOException if an orphaned output file cannot be deleted.
   */
  public Iterable<DefaultOutputMetadata> deleteStaleOutputs(boolean eager) throws IOException {
    List<DefaultOutputMetadata> deleted = new ArrayList<DefaultOutputMetadata>();

    oldOutputs: for (File outputFile : oldState.outputs.keySet()) {
      // keep if output file was registered during this build
      if (processedOutputs.containsKey(outputFile)) {
        continue oldOutputs;
      }

      Collection<Object> associatedInputs = oldState.outputInputs.get(outputFile);
      if (associatedInputs != null) {
        for (Object inputResource : associatedInputs) {

          // input is registered and not processed, not orphaned
          if (state.inputs.containsKey(inputResource)
              && !processedInputs.containsKey(inputResource)) {
            continue oldOutputs;
          }

          final DefaultInput<?> input = processedInputs.get(inputResource);
          // if not eager, let the caller deal with the outputs
          if (input != null && (!eager || isAssociatedOutput(input, outputFile))) {
            // the oldOutput is associated with an input, not orphaned
            continue oldOutputs;
          }
        }
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

  protected void deleteStaleOutput(File outputFile) throws IOException {
    if (outputFile.exists() && !outputFile.delete()) {
      throw new IOException("Could not delete file " + outputFile);
    }
  }

  private static <K, V> void put(Map<K, Collection<V>> multimap, K key, V value) {
    Collection<V> values = multimap.get(key);
    if (values == null) {
      values = new LinkedHashSet<V>();
      multimap.put(key, values);
    }
    values.add(value);
  }

  @Override
  public DefaultOutput processOutput(File outputFile) {
    outputFile = normalize(outputFile);

    DefaultOutput output = processedOutputs.get(outputFile);
    if (output == null) {
      output = new DefaultOutput(this, state, outputFile);
      processedOutputs.put(outputFile, output);
    }

    return output;
  }

  public ResourceStatus getInputStatus(Object inputResource, boolean associated) {
    if (!state.inputs.containsKey(inputResource)) {
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

    ResourceStatus status = oldInputState.getStatus();

    if (status != ResourceStatus.UNMODIFIED) {
      return status;
    }

    if (associated) {
      Collection<Object> includedInputs = oldState.inputIncludedInputs.get(inputResource);
      if (includedInputs != null) {
        for (Object includedInput : includedInputs) {
          ResourceHolder<?> includedInputState = oldState.inputs.get(includedInput);
          if (includedInputState.getStatus() != ResourceStatus.UNMODIFIED) {
            return ResourceStatus.MODIFIED;
          }
        }
      }

      Collection<File> outputFiles = oldState.inputOutputs.get(inputResource);
      if (outputFiles != null) {
        for (File outputFile : outputFiles) {
          ResourceHolder<File> outputState = oldState.outputs.get(outputFile);
          if (outputState.getStatus() != ResourceStatus.UNMODIFIED) {
            return ResourceStatus.MODIFIED;
          }
        }
      }
    }

    return ResourceStatus.UNMODIFIED;
  }

  public ResourceStatus getOutputStatus(File outputFile) {
    ResourceHolder<File> oldOutputState =
        oldState != null ? oldState.outputs.get(outputFile) : null;

    if (oldOutputState == null) {
      if (state.outputs.containsKey(outputFile)) {
        return ResourceStatus.NEW;
      }
      throw new IllegalArgumentException("Output is not processed " + outputFile);
    }

    return oldOutputState.getStatus();
  }

  @Override
  public DefaultInputMetadata<File> registerInput(File inputFile) {
    return registerInput(new FileState(normalize(inputFile)));
  }

  public <T extends Serializable> DefaultInputMetadata<T> registerInput(ResourceHolder<T> holder) {
    T resource = holder.getResource();

    if (holder.getStatus() == ResourceStatus.REMOVED) {
      throw new IllegalArgumentException("Input does not exist " + resource);
    }

    state.inputs.put(resource, holder);

    // this returns different instance each invocation. This should not be a problem because
    // each instance is a stateless flyweight.

    return new DefaultInputMetadata<T>(this, oldState, resource);
  }

  @Override
  public Iterable<DefaultInputMetadata<File>> registerInputs(Iterable<File> inputFiles) {
    Map<File, DefaultInputMetadata<File>> result =
        new LinkedHashMap<File, DefaultInputMetadata<File>>();
    for (File inputFile : inputFiles) {
      result.put(inputFile, registerInput(inputFile));
    }
    return result.values();
  }

  @Override
  public Iterable<DefaultInputMetadata<File>> getRegisteredInputs() {
    return getRegisteredInputs(File.class);
  }

  public <T> Iterable<DefaultInputMetadata<T>> getRegisteredInputs(Class<T> clazz) {
    Set<DefaultInputMetadata<T>> result = new LinkedHashSet<DefaultInputMetadata<T>>();
    for (Object inputResource : state.inputs.keySet()) {
      if (clazz.isInstance(inputResource)) {
        DefaultInputMetadata<T> input = getProcessedInput(clazz.cast(inputResource));
        if (input == null) {
          input = new DefaultInputMetadata<T>(this, state, clazz.cast(inputResource));
        }
        result.add(input);
      }
    }
    if (oldState != null) {
      for (Object inputResource : oldState.inputs.keySet()) {
        if (!state.inputs.containsKey(inputResource) && clazz.isInstance(inputResource)) {
          // removed
          result.add(new DefaultInputMetadata<T>(this, oldState, clazz.cast(inputResource)));
        }
      }
    }
    return result;
  }

  @Override
  public Iterable<DefaultOutputMetadata> getProcessedOutputs() {
    Set<DefaultOutputMetadata> result = new LinkedHashSet<DefaultOutputMetadata>();
    for (DefaultOutput output : processedOutputs.values()) {
      result.add(output);
    }
    if (oldState != null) {
      for (File outputFile : oldState.outputs.keySet()) {
        if (!processedOutputs.containsKey(outputFile)) {
          Collection<Object> associatedInputs = oldState.outputInputs.get(outputFile);
          if (associatedInputs != null) {
            for (Object inputResource : associatedInputs) {
              if (state.inputs.containsKey(inputResource)
                  && !processedInputs.containsKey(inputResource)) {
                result.add(new DefaultOutputMetadata(this, oldState, outputFile));
                break;
              }
            }
          }
        }
      }
    }
    return result;
  }

  private File normalize(File file) {
    if (file == null) {
      return null;
    }
    try {
      return file.getCanonicalFile();
    } catch (IOException e) {
      log.debug("Could not normalize file {}", file, e);
      return file.getAbsoluteFile();
    }
  }

  // association management

  public void associate(DefaultInput<?> input, DefaultOutput output) {
    Object inputResource = input.getResource();
    if (!processedInputs.containsKey(inputResource)) {
      throw new IllegalStateException("Input is not processed " + inputResource);
    }
    File outputFile = output.getResource();
    put(state.inputOutputs, inputResource, outputFile);
    put(state.outputInputs, outputFile, inputResource);
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

  public void associateIncludedInput(DefaultInput<?> input, DefaultInputMetadata<?> included) {
    put(state.inputIncludedInputs, input.getResource(), included.getResource());
  }

  // provided/required capability matching

  void addRequirement(DefaultInput<?> input, String qualifier, String localName) {
    addRequirement(input, new QualifiedName(qualifier, localName));
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
              && oldInputState.getStatus() != ResourceStatus.REMOVED) {
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

  public <T extends Serializable> T getResourceAttribute(File resource, String key, Class<T> clazz) {
    Map<String, Serializable> attributes = state.resourceAttributes.get(resource);
    return attributes != null ? clazz.cast(attributes.get(key)) : null;
  }

  // messages

  public void addMessage(DefaultInput<?> input, int line, int column, String message, int severity,
      Throwable cause) {
    put(state.inputMessages, input.getResource(), new Message(line, column, message, severity,
        cause));

    if (severity == SEVERITY_ERROR) {
      errorCount.incrementAndGet();
    }

    // echo message
    logMessage(input.getResource(), line, column, message, severity, cause);
  }

  public void commit() throws BuildFailureException, IOException {
    deleteStaleOutputs(true);

    // carry over relevant parts of the old state

    for (Object inputResource : oldState.inputs.keySet()) {
      if (!processedInputs.containsKey(inputResource) && state.inputs.containsKey(inputResource)) {
        // copy associated outputs
        Collection<File> associatedOutputs = oldState.inputOutputs.get(inputResource);
        if (associatedOutputs != null) {
          for (File outputFile : associatedOutputs) {
            carryOverOutput(inputResource, outputFile);
          }
        }

        // copy associated included inputs
        Collection<Object> includedInputs = oldState.inputIncludedInputs.get(inputResource);
        if (includedInputs != null) {
          state.inputIncludedInputs.put(inputResource, new LinkedHashSet<Object>(includedInputs));

          for (Object includedInput : includedInputs) {
            ResourceHolder<?> oldHolder = oldState.inputs.get(includedInput);
            state.inputs.put(oldHolder.getResource(), oldHolder);
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
        Collection<Message> messages = oldState.inputMessages.get(inputResource);
        if (messages != null) {
          state.inputMessages.put(inputResource, new ArrayList<Message>(messages));

          // replay old messages
          for (Message message : messages) {
            logMessage(inputResource, message.line, message.column, message.message,
                message.severity, message.cause);
            if (message.severity == SEVERITY_ERROR) {
              errorCount.incrementAndGet();
            }
          }
        }

        // copy attributes
        Map<String, Serializable> attributes = oldState.resourceAttributes.get(inputResource);
        if (attributes != null) {
          state.resourceAttributes.put(inputResource, attributes);
        }
      }
    }

    for (ResourceHolder<?> resource : state.inputs.values()) {
      if (resource.getStatus() != ResourceStatus.UNMODIFIED) {
        throw new IllegalStateException("Unexpected input change " + resource.getResource());
      }
    }

    storeState();

    if (errorCount.get() > 0) {
      throw newBuildFailureException(errorCount.get());
    }
  }

  protected void carryOverOutput(Object inputResource, File outputFile) {
    processedOutputs.put(outputFile, new DefaultOutput(this, state, outputFile));

    state.outputs.put(outputFile, oldState.outputs.get(outputFile));

    put(state.inputOutputs, inputResource, outputFile);
    put(state.outputInputs, outputFile, inputResource);

    Collection<QualifiedName> capabilities = oldState.outputCapabilities.get(outputFile);
    if (capabilities != null) {
      state.outputCapabilities.put(outputFile, new LinkedHashSet<QualifiedName>(capabilities));
    }

    Map<String, Serializable> attributes = oldState.resourceAttributes.get(outputFile);
    if (attributes != null) {
      state.resourceAttributes.put(outputFile, attributes);
    }
  }

  protected abstract void logMessage(Object inputResource, int line, int column, String message,
      int severity, Throwable cause);

  // XXX not too happy with errorCount parameter
  protected abstract BuildFailureException newBuildFailureException(int errorCount);
}
