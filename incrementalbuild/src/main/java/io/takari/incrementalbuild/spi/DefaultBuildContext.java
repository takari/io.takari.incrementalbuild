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
  private final Map<File, DefaultInput> processedInputs = new HashMap<File, DefaultInput>();

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
    if (oldState == null) {
      log.debug("No previous build state {}", stateFile);
      return true;
    }

    Map<String, byte[]> oldConfiguration = oldState.getConfiguration();

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
    return null;
  }

  private void storeState() throws IOException {
    // timestamp output files
    for (File outputFile : processedOutputs.keySet()) {
      state.outputs.put(outputFile, new FileState(outputFile));
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

  public DefaultInput processInput(InputMetadata<File> inputMetadata) {
    if (inputMetadata instanceof DefaultInput && ((DefaultInput) inputMetadata).context == this) {
      return (DefaultInput) inputMetadata;
    }

    if (!(inputMetadata instanceof DefaultInputMetadata)
        || ((DefaultInputMetadata) inputMetadata).context != this) {
      throw new IllegalArgumentException();
    }

    File inputFile = inputMetadata.getResource();
    if (!state.inputs.containsKey(inputFile)) {
      throw new IllegalArgumentException("Unregistered input file " + inputFile);
    }


    DefaultInput input = processedInputs.get(inputFile);
    if (input == null) {
      input = put(processedInputs, inputFile, new DefaultInput(this, inputFile));
    }

    return input;
  }

  private void putInputFileState(File inputFile, FileState fileState) {
    FileState oldFileState = state.inputs.put(inputFile, fileState);
    if (oldFileState != null && !FileState.equals(oldFileState, fileState)) {
      throw new IllegalStateException("Unexpected input file change " + inputFile);
    }
  }

  @Override
  public Iterable<DefaultInput> registerAndProcessInputs(Iterable<File> inputFiles) {
    List<DefaultInput> inputs = new ArrayList<DefaultInput>();
    for (DefaultInputMetadata metadata : registerInputs(inputFiles)) {
      DefaultInput input = processedInputs.get(metadata.getResource());
      if (input == null) {
        if (metadata.getStatus() != ResourceStatus.UNMODIFIED
            || !isUptodate(metadata.getAssociatedOutputs())) {
          input = processInput(metadata);
        }
      }
      if (input != null) {
        inputs.add(input);
      }
    }
    return inputs;
  }

  private boolean isUptodate(Iterable<? extends OutputMetadata<File>> outputs) {
    for (OutputMetadata<File> output : outputs) {
      if (output.getStatus() != ResourceStatus.UNMODIFIED) {
        return false;
      }
    }
    return true;
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
    if (oldState == null) {
      return Collections.emptyList();
    }

    List<DefaultOutputMetadata> deleted = new ArrayList<DefaultOutputMetadata>();

    oldOutputs: for (File outputFile : oldState.getOutputFiles()) {
      // keep if output file was registered during this build
      if (processedOutputs.containsKey(outputFile)) {
        continue oldOutputs;
      }

      for (File inputFile : oldState.getAssociatedInputs(outputFile)) {

        // input is registered and not processed, not orphaned
        if (state.inputs.containsKey(inputFile) && !processedInputs.containsKey(inputFile)) {
          continue oldOutputs;
        }

        final DefaultInput input = processedInputs.get(inputFile);
        // if not eager, let the caller deal with the outputs
        if (input != null && (!eager || input.isAssociatedOutput(outputFile))) {
          // the oldOutput is associated with an input, not orphaned
          continue oldOutputs;
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

  // XXX inline!
  private static <K, V> V put(Map<K, V> map, K key, V value) {
    map.put(key, value);
    return value;
  }

  @Override
  public DefaultOutput processOutput(File outputFile) {
    outputFile = normalize(outputFile);

    DefaultOutput output = processedOutputs.get(outputFile);
    if (output == null) {
      output = put(processedOutputs, outputFile, new DefaultOutput(this, outputFile));
    }

    return output;
  }

  public ResourceStatus getInputStatus(File inputFile) {
    if (!state.inputs.containsKey(inputFile)) {
      if (oldState != null && oldState.getInputFiles().contains(inputFile)) {
        return ResourceStatus.REMOVED;
      }
      throw new IllegalArgumentException("Unregistered input file " + inputFile);
    }
    if (oldState == null) {
      return ResourceStatus.NEW;
    }
    ResourceStatus status = oldState.getInputStatus(inputFile);
    if (status == ResourceStatus.UNMODIFIED) {
      for (File outputFile : oldState.getAssociatedOutputs(inputFile)) {
        if (oldState.getOutputStatus(outputFile) != ResourceStatus.UNMODIFIED) {
          status = ResourceStatus.MODIFIED;
          break;
        }
      }
    }
    if (status == ResourceStatus.UNMODIFIED && escalated) {
      status = ResourceStatus.MODIFIED;
    }
    return status;
  }

  public ResourceStatus getOutputStatus(File outputFile) {
    if (!FileState.isPresent(outputFile)) {
      if (oldState != null && oldState.getOutputFiles().contains(outputFile)) {
        return ResourceStatus.REMOVED;
      }
      throw new IllegalArgumentException("Output does not exist " + outputFile);
    }

    if (oldState == null) {
      return ResourceStatus.NEW;
    }

    return oldState.getOutputStatus(outputFile);
  }

  @Override
  public DefaultInputMetadata registerInput(File inputFile) {
    if (!FileState.isPresent(inputFile)) {
      throw new IllegalArgumentException("Input file does not exist or cannot be read " + inputFile);
    }

    inputFile = normalize(inputFile);

    putInputFileState(inputFile, new FileState(inputFile));

    // XXX this returns different instance each invocation. This should not be a problem because
    // each instance is a stateless flyweight.

    return new DefaultInputMetadata(this, oldState, inputFile);
  }

  @Override
  public Iterable<DefaultInputMetadata> registerInputs(Iterable<File> inputFiles) {
    Map<File, DefaultInputMetadata> result = new LinkedHashMap<File, DefaultInputMetadata>();
    for (File inputFile : inputFiles) {
      result.put(inputFile, registerInput(inputFile));
    }
    return result.values();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Iterable<? extends InputMetadata<T>> getRegisteredInputs(Class<T> clazz) {
    if (!File.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("Only java.io.File is currently supported " + clazz);
    }
    Set<InputMetadata<T>> result = new LinkedHashSet<InputMetadata<T>>();
    for (File inputFile : state.inputs.keySet()) {
      InputMetadata<T> input = (InputMetadata<T>) processedInputs.get(inputFile);
      if (input == null) {
        input = (InputMetadata<T>) new DefaultInputMetadata(this, state, inputFile);
      }
      result.add(input);
    }
    if (oldState != null) {
      for (File inputFile : oldState.getInputFiles()) {
        if (!state.inputs.containsKey(inputFile)) {
          // removed
          result.add((InputMetadata<T>) new DefaultInputMetadata(this, oldState, inputFile));
        }
      }
    }
    return result;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Iterable<? extends OutputMetadata<T>> getProcessedOutputs(Class<T> clazz) {
    if (!File.class.isAssignableFrom(clazz)) {
      throw new IllegalArgumentException("Only java.io.File is currently supported " + clazz);
    }
    Set<OutputMetadata<T>> result = new LinkedHashSet<OutputMetadata<T>>();
    for (DefaultOutput output : processedOutputs.values()) {
      result.add((OutputMetadata<T>) output);
    }
    if (oldState != null) {
      for (File outputFile : oldState.getOutputFiles()) {
        if (!processedOutputs.containsKey(outputFile)) {
          for (File inputFile : oldState.getAssociatedInputs(outputFile)) {
            if (state.inputs.containsKey(inputFile) && !processedInputs.containsKey(inputFile)) {
              result.add((OutputMetadata<T>) new DefaultOutputMetadata(this, oldState, outputFile));
              break;
            }
          }
        }
      }
    }
    return result;
  }

  private File normalize(File file) {
    try {
      return file.getCanonicalFile();
    } catch (IOException e) {
      log.debug("Could not normalize file {}", file, e);
      return file.getAbsoluteFile();
    }
  }

  // association management

  public DefaultOutput associateOutput(DefaultInput input, File outputFile) {
    DefaultOutput output = processOutput(outputFile);
    associate(input, output);
    return output;
  }

  public void associate(InputMetadata<File> input, DefaultOutput output) {
    File inputFile = input.getResource();
    if (!processedInputs.containsKey(inputFile)) {
      throw new IllegalStateException("Input is not processed " + inputFile);
    }

    File outputFile = output.getResource();
    Collection<File> outputs = state.inputOutputs.get(inputFile);
    if (outputs == null) {
      outputs = put(state.inputOutputs, inputFile, new LinkedHashSet<File>());
    }
    outputs.add(outputFile);

    Collection<File> inputs = state.outputInputs.get(outputFile);
    if (inputs == null) {
      inputs = put(state.outputInputs, outputFile, new LinkedHashSet<File>());
    }
    inputs.add(inputFile);
  }

  public boolean isAssociatedOutput(DefaultInput input, File outputFile) {
    Collection<File> outputs = state.inputOutputs.get(input.getResource());
    return outputs != null && outputs.contains(outputFile);
  }

  public Iterable<DefaultInput> getAssociatedInputs(File outputFile) {
    Collection<File> inputFiles = state.outputInputs.get(outputFile);
    if (inputFiles == null || inputFiles.isEmpty()) {
      return Collections.emptyList();
    }
    List<DefaultInput> inputs = new ArrayList<DefaultInput>();
    for (File inputFile : inputFiles) {
      inputs.add(processedInputs.get(inputFile));
    }
    return inputs;
  }

  Iterable<DefaultInputMetadata> getAssociatedInputs(DefaultBuildContextState state, File outputFile) {
    Collection<File> inputFiles = state.outputInputs.get(outputFile);
    if (inputFiles == null || inputFiles.isEmpty()) {
      return Collections.emptyList();
    }
    List<DefaultInputMetadata> inputs = new ArrayList<DefaultInputMetadata>();
    for (File inputFile : inputFiles) {
      inputs.add(new DefaultInputMetadata(this, state, inputFile));
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
      File inputFile) {
    Collection<File> outputFiles = state.inputOutputs.get(inputFile);
    if (outputFiles == null || outputFiles.isEmpty()) {
      return Collections.emptyList();
    }
    List<DefaultOutputMetadata> outputs = new ArrayList<DefaultOutputMetadata>();
    for (File outputFile : outputFiles) {
      outputs.add(new DefaultOutputMetadata(this, state, outputFile));
    }
    return outputs;
  }

  public void associateIncludedInput(DefaultInput input, File includedFile) {
    File inputFile = input.getResource();
    Collection<File> includedFiles = state.inputIncludedInputs.get(inputFile);
    if (includedFiles == null) {
      includedFiles = put(state.inputIncludedInputs, inputFile, new LinkedHashSet<File>());
    }
    includedFiles.add(includedFile);
    putInputFileState(includedFile, new FileState(includedFile));
  }

  // provided/required capability matching

  public void addRequirement(DefaultInput input, String qualifier, String localName) {
    addRequirement(input, new QualifiedName(qualifier, localName));
  }

  private void addRequirement(DefaultInput input, QualifiedName requirement) {
    File inputFile = input.getResource();
    Collection<File> inputs = state.requirementInputs.get(requirement);
    if (inputs == null) {
      inputs = put(state.requirementInputs, requirement, new LinkedHashSet<File>());
    }
    inputs.add(inputFile);

    Collection<QualifiedName> requirements = state.inputRequirements.get(inputFile);
    if (requirements == null) {
      requirements = put(state.inputRequirements, inputFile, new LinkedHashSet<QualifiedName>());
    }
    requirements.add(requirement);
  }

  public void addCapability(DefaultOutput output, String qualifier, String localName) {
    File outputFile = output.getResource();
    Collection<QualifiedName> capabilities = state.outputCapabilities.get(outputFile);
    if (capabilities == null) {
      capabilities = put(state.outputCapabilities, outputFile, new LinkedHashSet<QualifiedName>());
    }
    capabilities.add(new QualifiedName(qualifier, localName));
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
  public Iterable<? extends InputMetadata<File>> getDependentInputs(String qualifier,
      String localName) {
    Map<File, InputMetadata<File>> result = new LinkedHashMap<File, InputMetadata<File>>();

    Collection<File> inputs = state.requirementInputs.get(new QualifiedName(qualifier, localName));
    if (inputs != null) {
      for (File inputFile : inputs) {
        result.put(inputFile, processedInputs.get(inputFile));
      }
    }

    if (oldState != null) {
      for (File inputFile : oldState.getDependentInputs(qualifier, localName)) {
        if (!result.containsKey(inputFile) && FileState.isPresent(inputFile)) {
          result.put(inputFile, registerInput(inputFile));
        }
      }
    }

    return result.values();
  }

  // simple key/value pairs

  public <T extends Serializable> Serializable setResourceAttribute(File resource, String key,
      T value) {
    Map<String, Serializable> attributes = state.resourceAttributes.get(resource);
    if (attributes == null) {
      attributes =
          put(state.resourceAttributes, resource, new LinkedHashMap<String, Serializable>());
    }
    attributes.put(key, value);
    if (oldState != null) {
      return oldState.getResourceAttribute(resource, key, Serializable.class);
    }
    return null;
  }

  public <T extends Serializable> T getResourceAttribute(File resource, String key, Class<T> clazz) {
    Map<String, Serializable> attributes = state.resourceAttributes.get(resource);
    return attributes != null ? clazz.cast(attributes.get(key)) : null;
  }

  // messages

  public void addMessage(DefaultInput input, int line, int column, String message, int severity,
      Throwable cause) {
    File inputFile = input.getResource();
    Collection<Message> messages = state.inputMessages.get(inputFile);
    if (messages == null) {
      messages = put(state.inputMessages, inputFile, new ArrayList<Message>());
    }
    messages.add(new Message(line, column, message, severity, cause));
    if (severity == SEVERITY_ERROR) {
      errorCount.incrementAndGet();
    }

    // echo message
    logMessage(input, line, column, message, severity, cause);
  }

  public void commit() throws BuildFailureException, IOException {
    deleteStaleOutputs(true);

    // carry over relevant parts of the old state

    if (oldState != null) {
      for (File inputFile : oldState.getInputFiles()) {
        if (!processedInputs.containsKey(inputFile) && state.inputs.containsKey(inputFile)) {
          DefaultInput input = put(processedInputs, inputFile, new DefaultInput(this, inputFile));

          // copy associated outputs
          for (File outputFile : oldState.getAssociatedOutputs(inputFile)) {
            carryOverOutput(input, outputFile);
          }

          // copy associated included inputs
          Collection<File> includedInputs = oldState.getInputIncludedInputs(inputFile);
          if (includedInputs != null) {
            state.inputIncludedInputs.put(inputFile, new LinkedHashSet<File>(includedInputs));

            for (File includedInput : includedInputs) {
              putInputFileState(includedInput, oldState.getInputState(includedInput));
            }
          }

          // copy requirements
          Collection<QualifiedName> requirements = oldState.getInputRequirements(inputFile);
          if (requirements != null) {
            for (QualifiedName requirement : requirements) {
              addRequirement(input, requirement);
            }
          }

          // copy messages
          Collection<Message> messages = oldState.getInputMessages(inputFile);
          if (messages != null) {
            state.inputMessages.put(inputFile, new ArrayList<Message>(messages));

            // replay old messages
            for (Message message : messages) {
              logMessage(input, message.line, message.column, message.message, message.severity,
                  message.cause);
              if (message.severity == SEVERITY_ERROR) {
                errorCount.incrementAndGet();
              }
            }
          }

          // copy attributes
          Map<String, Serializable> attributes = oldState.getResourceAttributes(inputFile);
          if (attributes != null) {
            state.resourceAttributes.put(inputFile, attributes);
          }
        }
      }
    }

    for (Map.Entry<File, FileState> entry : state.inputs.entrySet()) {
      if (!FileState.equals(new FileState(entry.getKey()), entry.getValue())) {
        throw new IllegalStateException("Unexpected input file change " + entry.getKey());
      }
    }


    storeState();

    if (errorCount.get() > 0) {
      throw newBuildFailureException(errorCount.get());
    }
  }

  protected void carryOverOutput(DefaultInput input, File outputFile) {
    associate(input, put(processedOutputs, outputFile, new DefaultOutput(this, outputFile)));

    Collection<QualifiedName> capabilities = oldState.getOutputCapabilities(outputFile);
    if (capabilities != null) {
      state.outputCapabilities.put(outputFile, new LinkedHashSet<QualifiedName>(capabilities));
    }

    Map<String, Serializable> attributes = oldState.getResourceAttributes(outputFile);
    if (attributes != null) {
      state.resourceAttributes.put(outputFile, attributes);
    }
  }

  protected abstract void logMessage(DefaultInput input, int line, int column, String message,
      int severity, Throwable cause);

  // XXX not too happy with errorCount parameter
  protected abstract BuildFailureException newBuildFailureException(int errorCount);
}
