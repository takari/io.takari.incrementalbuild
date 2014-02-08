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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

  private final DefaultBuildContextState oldState;

  private final BuildContextState oldStateAdaptor;

  private final BuildContextState stateAdaptor = new BuildContextState() {

    @Override
    public <V extends Serializable> V getPropertyValue(File input, String key, Class<V> clazz) {
      Map<String, Serializable> attributes = inputAttributes.get(input);
      return attributes != null ? clazz.cast(attributes.get(key)) : null;
    }

    @Override
    public Collection<String> getOutputCapabilities(File outputFile, String qualifier) {
      return DefaultBuildContext.this.getOutputCapabilities(outputFile, qualifier);
    }

    @Override
    public Iterable<? extends OutputMetadata<File>> getAssociatedOutputs(File file) {
      return DefaultBuildContext.this.getAssociatedOutputs(file);
    }

    @Override
    public Iterable<? extends InputMetadata<File>> getAssociatedInputs(File file) {
      return DefaultBuildContext.this.getAssociatedInputs(file);
    }
  };

  private final Map<String, byte[]> configuration;

  /**
   * Previous build state does not exist, cannot be read or configuration has changed. When
   * escalated, all input files are considered require processing.
   */
  private final boolean escalated;

  // inputs and outputs

  /**
   * All inputs registered during this build.
   */
  private final ConcurrentMap<File, FileState> registeredInputs =
      new ConcurrentHashMap<File, FileState>();

  /**
   * All inputs selected for processing during this build.
   */
  private final ConcurrentMap<File, DefaultInput> processedInputs =
      new ConcurrentHashMap<File, DefaultInput>();

  /**
   * Outputs registered with this build context during this build.
   */
  private final ConcurrentMap<File, DefaultOutput> processedOutputs =
      new ConcurrentHashMap<File, DefaultOutput>();

  private final Set<File> deletedOutputs = new HashSet<File>();

  // direct associations

  private final ConcurrentMap<File, Collection<File>> inputOutputs =
      new ConcurrentHashMap<File, Collection<File>>();

  private final ConcurrentMap<File, Collection<File>> outputInputs =
      new ConcurrentHashMap<File, Collection<File>>();

  private final ConcurrentMap<File, Collection<File>> inputIncludedInputs =
      new ConcurrentHashMap<File, Collection<File>>();

  // provided/required capabilities

  private final ConcurrentMap<File, Collection<QualifiedName>> inputRequirements =
      new ConcurrentHashMap<File, Collection<QualifiedName>>();

  /**
   * Maps requirement qname to all input that require it.
   */
  private final ConcurrentMap<QualifiedName, Collection<File>> requirementInputs =
      new ConcurrentHashMap<QualifiedName, Collection<File>>();

  /**
   * Maps output file to capabilities provided by it.
   */
  private final ConcurrentMap<File, Collection<QualifiedName>> outputCapabilities =
      new ConcurrentHashMap<File, Collection<QualifiedName>>();

  // simple key/value pairs

  private final ConcurrentMap<File, Map<String, Serializable>> inputAttributes =
      new ConcurrentHashMap<File, Map<String, Serializable>>();

  // messages

  /**
   * Maps input or included input file to messages generated for the file
   */
  private final ConcurrentMap<File, Collection<Message>> inputMessages =
      new ConcurrentHashMap<File, Collection<Message>>();

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
    this.oldState = loadState(stateFile);

    BuildContextState oldStateAdaptor = stateAdaptor; // XXX should be empty state
    if (oldState != null) {
      oldStateAdaptor = new BuildContextState() {

        @Override
        public <V extends Serializable> V getPropertyValue(File input, String key, Class<V> clazz) {
          return oldState.getValue(input, key, clazz);
        }

        @Override
        public Collection<String> getOutputCapabilities(File outputFile, String qualifier) {
          return oldState.getCapabilities(outputFile, qualifier);
        }

        @Override
        public Iterable<? extends OutputMetadata<File>> getAssociatedOutputs(File inputFIle) {
          Collection<File> outputFiles = oldState.getAssociatedOutputs(inputFIle);
          if (outputFiles == null || outputFiles.isEmpty()) {
            return Collections.emptyList();
          }
          List<OutputMetadata<File>> result = new ArrayList<BuildContext.OutputMetadata<File>>();
          for (File outputFile : outputFiles) {
            result.add(new DefaultOutputMetadata(DefaultBuildContext.this, this, outputFile));
          }
          return result;
        }

        @Override
        public Iterable<? extends InputMetadata<File>> getAssociatedInputs(File outputFile) {
          Collection<File> inputFiles = oldState.getAssociatedInputs(outputFile);
          if (inputFiles == null || inputFiles.isEmpty()) {
            return Collections.emptyList();
          }
          List<InputMetadata<File>> result = new ArrayList<BuildContext.InputMetadata<File>>();
          for (File inputFile : inputFiles) {
            result.add(new DefaultInputMetadata(DefaultBuildContext.this, this, inputFile));
          }
          return result;
        }
      };
    }
    this.oldStateAdaptor = oldStateAdaptor;

    // TODO clone byte arrays too?
    this.configuration = new HashMap<String, byte[]>(configuration);

    this.escalated = getEscalated();
  }

  private boolean getEscalated() {
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
    final Map<File, FileState> files = new HashMap<File, FileState>(registeredInputs);
    for (File output : processedOutputs.keySet()) {
      files.put(output, new FileState(output));
    }

    final DefaultBuildContextState state =
        new DefaultBuildContextState(configuration, files, registeredInputs.keySet(),
            processedOutputs.keySet(), inputOutputs, outputInputs, inputIncludedInputs,
            inputRequirements, requirementInputs, outputCapabilities, inputAttributes,
            inputMessages);

    File parent = stateFile.getParentFile();
    if (!parent.isDirectory() && !parent.mkdirs()) {
      throw new IOException("Could not create direcotyr " + parent);
    }

    ObjectOutputStream os =
        new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(
            stateFile)))) {
          {
            enableReplaceObject(true);
          }

          @Override
          protected Object replaceObject(Object obj) throws IOException {
            // TODO maybe don't serialize DefaultInput/DefaultOutput instances?
            if (obj == DefaultBuildContext.this) {
              return state;
            }
            return super.replaceObject(obj);
          }
        };
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
    if (!registeredInputs.containsKey(inputFile)) {
      throw new IllegalArgumentException("Unregistered input file " + inputFile);
    }


    DefaultInput input = processedInputs.get(inputFile);
    if (input == null) {
      input = putIfAbsent(processedInputs, inputFile, new DefaultInput(this, inputFile));
    }

    return input;
  }

  private void putInputFileState(File inputFile, FileState fileState) {
    FileState oldFileState = registeredInputs.put(inputFile, fileState);
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
        if (registeredInputs.containsKey(inputFile) && !processedInputs.containsKey(inputFile)) {
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

      deleted.add(new DefaultOutputMetadata(this, oldStateAdaptor, outputFile));
    }
    return deleted;
  }

  protected void deleteStaleOutput(File outputFile) throws IOException {
    if (outputFile.exists() && !outputFile.delete()) {
      throw new IOException("Could not delete file " + outputFile);
    }
  }

  private static <K, V> V putIfAbsent(ConcurrentMap<K, V> map, K key, V value) {
    // XXX do I do this right? need to check with the concurrency book
    map.putIfAbsent(key, value);
    return map.get(key);
  }

  @Override
  public DefaultOutput processOutput(File outputFile) {
    outputFile = normalize(outputFile);

    DefaultOutput output = processedOutputs.get(outputFile);
    if (output == null) {
      output = putIfAbsent(processedOutputs, outputFile, new DefaultOutput(this, outputFile));
    }

    return output;
  }

  public DefaultOutputMetadata getOldOutput(File outputFile) {
    outputFile = normalize(outputFile);
    if (oldState == null || !oldState.getOutputFiles().contains(outputFile)) {
      return null;
    }
    return new DefaultOutputMetadata(this, oldStateAdaptor, outputFile);
  }

  public DefaultInputMetadata getOldInput(File inputFile) {
    if (oldState == null || !oldState.getInputFiles().contains(inputFile)) {
      return null;
    }
    return new DefaultInputMetadata(this, oldStateAdaptor, inputFile);
  }

  public ResourceStatus getInputStatus(File inputFile) {
    if (!registeredInputs.containsKey(inputFile)) {
      if (oldState != null && oldState.getInputFiles().contains(inputFile)) {
        return ResourceStatus.REMOVED;
      }
      throw new IllegalArgumentException("Unregistered input file " + inputFile);
    }
    if (oldState == null) {
      return ResourceStatus.NEW;
    }
    ResourceStatus status = oldState.getInputStatus(inputFile);
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

    return new DefaultInputMetadata(this, oldStateAdaptor, inputFile);
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
    for (File inputFile : registeredInputs.keySet()) {
      InputMetadata<T> input = (InputMetadata<T>) processedInputs.get(inputFile);
      if (input == null) {
        input = (InputMetadata<T>) new DefaultInputMetadata(this, stateAdaptor, inputFile);
      }
      result.add(input);
    }
    if (oldState != null) {
      for (File inputFile : oldState.getInputFiles()) {
        if (!registeredInputs.containsKey(inputFile)) {
          // removed
          result.add((InputMetadata<T>) new DefaultInputMetadata(this, oldStateAdaptor, inputFile));
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
    Collection<File> outputs = inputOutputs.get(inputFile);
    if (outputs == null) {
      outputs = putIfAbsent(inputOutputs, inputFile, new LinkedHashSet<File>());
    }
    outputs.add(outputFile); // XXX NOT THREAD SAFE

    Collection<File> inputs = outputInputs.get(outputFile);
    if (inputs == null) {
      inputs = putIfAbsent(outputInputs, outputFile, new LinkedHashSet<File>());
    }
    inputs.add(inputFile); // XXX NOT THREAD SAFE
  }

  public boolean isAssociatedOutput(DefaultInput input, File outputFile) {
    Collection<File> outputs = inputOutputs.get(input.getResource());
    return outputs != null && outputs.contains(outputFile);
  }

  public Iterable<DefaultInput> getAssociatedInputs(File outputFile) {
    Collection<File> inputFiles = outputInputs.get(outputFile);
    if (inputFiles == null || inputFiles.isEmpty()) {
      return Collections.emptyList();
    }
    List<DefaultInput> inputs = new ArrayList<DefaultInput>();
    for (File inputFile : inputFiles) {
      inputs.add(processedInputs.get(inputFile));
    }
    return inputs;
  }

  public Iterable<DefaultOutput> getAssociatedOutputs(File inputFile) {
    Collection<File> outputFiles = inputOutputs.get(inputFile);
    if (inputOutputs == null || inputOutputs.isEmpty()) {
      return Collections.emptyList();
    }
    List<DefaultOutput> outputs = new ArrayList<DefaultOutput>();
    for (File outputFile : outputFiles) {
      outputs.add(this.processedOutputs.get(outputFile));
    }
    return outputs;
  }

  public void associateIncludedInput(DefaultInput input, File includedFile) {
    File inputFile = input.getResource();
    Collection<File> includedFiles = inputIncludedInputs.get(inputFile);
    if (includedFiles == null) {
      includedFiles = putIfAbsent(inputIncludedInputs, inputFile, new LinkedHashSet<File>());
    }
    includedFiles.add(includedFile); // XXX NOT THREAD SAFE
    putInputFileState(includedFile, new FileState(includedFile));
  }

  // provided/required capability matching

  public void addRequirement(DefaultInput input, String qualifier, String localName) {
    addRequirement(input, new QualifiedName(qualifier, localName));
  }

  private void addRequirement(DefaultInput input, QualifiedName requirement) {
    File inputFile = input.getResource();
    Collection<File> inputs = requirementInputs.get(requirement);
    if (inputs == null) {
      inputs = putIfAbsent(requirementInputs, requirement, new LinkedHashSet<File>());
    }
    inputs.add(inputFile); // XXX NOT THREAD SAFE

    Collection<QualifiedName> requirements = inputRequirements.get(inputFile);
    if (requirements == null) {
      requirements = putIfAbsent(inputRequirements, inputFile, new LinkedHashSet<QualifiedName>());
    }
    requirements.add(requirement); // XXX NOT THREAD SAFE
  }

  public void addCapability(DefaultOutput output, String qualifier, String localName) {
    File outputFile = output.getResource();
    Collection<QualifiedName> capabilities = outputCapabilities.get(outputFile);
    if (capabilities == null) {
      capabilities =
          putIfAbsent(outputCapabilities, outputFile, new LinkedHashSet<QualifiedName>());
    }
    capabilities.add(new QualifiedName(qualifier, localName)); // XXX NOT THREAD SAFE
  }

  public Collection<String> getOutputCapabilities(File outputFile, String qualifier) {
    Collection<QualifiedName> capabilities = outputCapabilities.get(outputFile);
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

    Collection<File> inputs = requirementInputs.get(new QualifiedName(qualifier, localName));
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

  public <T extends Serializable> void setValue(DefaultInput input, String key, T value) {
    File inputFile = input.getResource();
    Map<String, Serializable> attributes = inputAttributes.get(inputFile);
    if (attributes == null) {
      attributes =
          putIfAbsent(inputAttributes, inputFile, new LinkedHashMap<String, Serializable>());
    }
    attributes.put(key, value); // XXX NOT THREAD SAFE
  }

  public <T extends Serializable> T getValue(DefaultInput input, String key, Class<T> clazz) {
    Map<String, Serializable> attributes = inputAttributes.get(input.getResource());
    return attributes != null ? clazz.cast(attributes.get(key)) : null;
  }

  // messages

  public void addMessage(DefaultInput input, int line, int column, String message, int severity,
      Throwable cause) {
    File inputFile = input.getResource();
    Collection<Message> messages = this.inputMessages.get(inputFile);
    if (messages == null) {
      messages = putIfAbsent(inputMessages, inputFile, new ArrayList<Message>());
    }
    messages.add(new Message(line, column, message, severity, cause)); // XXX NOT THREAD SAFE
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
        if (!processedInputs.containsKey(inputFile) && registeredInputs.containsKey(inputFile)) {
          DefaultInput input =
              putIfAbsent(processedInputs, inputFile, new DefaultInput(this, inputFile));

          // copy associated outputs
          for (File outputFile : oldState.getAssociatedOutputs(inputFile)) {
            carryOverOutput(input, outputFile);
          }

          // copy associated included inputs
          Collection<File> includedInputs = oldState.getInputIncludedInputs(inputFile);
          if (includedInputs != null) {
            inputIncludedInputs.put(inputFile, new LinkedHashSet<File>(includedInputs));

            for (File includedInput : includedInputs) {
              putInputFileState(includedInput, oldState.getFileState(includedInput));
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
            inputMessages.put(inputFile, new ArrayList<Message>(messages));

            // replay old messages
            for (Message message : messages) {
              logMessage(input, message.line, message.column, message.message, message.severity,
                  message.cause);
              if (message.severity == SEVERITY_ERROR) {
                errorCount.incrementAndGet();
              }
            }
          }
        }
      }
    }

    for (Map.Entry<File, FileState> entry : registeredInputs.entrySet()) {
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
    associate(input, putIfAbsent(processedOutputs, outputFile, new DefaultOutput(this, outputFile)));

    Collection<QualifiedName> capabilities = oldState.getOutputCapabilities(outputFile);
    if (capabilities != null) {
      outputCapabilities.put(outputFile, new LinkedHashSet<QualifiedName>(capabilities));
    }
  }

  protected abstract void logMessage(DefaultInput input, int line, int column, String message,
      int severity, Throwable cause);

  // XXX not too happy with errorCount parameter
  protected abstract BuildFailureException newBuildFailureException(int errorCount);
}
