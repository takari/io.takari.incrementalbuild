package io.takari.incremental.internal;

import io.takari.incremental.BuildContext;
import io.takari.incremental.FileSet;
import io.takari.incremental.FileSetBuilder;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// XXX normalize all File paramters. maybe easier to use URI internally.
// XXX maybe use relative URIs to save heap

public class DefaultBuildContext implements BuildContext, BuildContextStateManager {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final File stateFile;

  private final BuildContextState oldState;

  private final Map<String, byte[]> configuration;

  /**
   * Previous build state does not exist, cannot be read or configuration has changed. When
   * escalated, all input files are considered require processing.
   */
  private final boolean escalated;

  // inputs and outputs

  /**
   * Inputs registered with this build context during this build.
   */
  private final ConcurrentMap<File, DefaultInput> inputs =
      new ConcurrentHashMap<File, DefaultInput>();

  /**
   * Outputs registered with this build context during this build.
   */
  private final ConcurrentMap<File, DefaultOutput> outputs =
      new ConcurrentHashMap<File, DefaultOutput>();

  // direct associations

  private final ConcurrentMap<File, Collection<DefaultOutput>> inputOutputs =
      new ConcurrentHashMap<File, Collection<DefaultOutput>>();

  private final ConcurrentMap<File, Collection<DefaultInput>> outputInputs =
      new ConcurrentHashMap<File, Collection<DefaultInput>>();

  private final ConcurrentMap<File, Collection<File>> inputIncludedInputs =
      new ConcurrentHashMap<File, Collection<File>>();

  /**
   * Maps requirement qname to all input that require it.
   */
  private final Map<QualifiedName, Set<DefaultInput>> requirementInputs =
      new HashMap<QualifiedName, Set<DefaultInput>>();

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

  private BuildContextState loadState(File stateFile) {
    // TODO verify stateFile location has not changed since last build
    try {
      ObjectInputStream is =
          new ObjectInputStream(new BufferedInputStream(new FileInputStream(stateFile)));
      try {
        return (BuildContextState) is.readObject();
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

    BuildContextState state =
        new BuildContextState(configuration, inputs, outputs, inputOutputs, inputIncludedInputs);

    ObjectOutputStream os =
        new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(stateFile)));
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

  @Override
  public DefaultInput processInput(File inputFile) {
    if (inputs.containsKey(inputFile)) {
      // skip, inputFile has been processed already
      return null;
    }
    if (escalated) {
      return registerInput(inputFile);
    }
    final DefaultInput oldInput = oldState.getInputs().get(inputFile);
    if (oldInput == null || oldInput.isProcessingRequired()) {
      return registerInput(inputFile);
    }
    return null;
  }

  @Override
  public Iterable<DefaultInput> processInputs(FileSet fileSet) {
    // reminder to self: don't optimize prematurely

    Set<DefaultInput> result = new LinkedHashSet<DefaultInput>();

    for (File inputFile : fileSet) {

      // can return the same input twice, if called concurrently from multiple threads

      if (inputs.containsKey(inputFile)) {
        // skip, this inputFile has been processed already
        continue;
      }

      final DefaultInput oldInput = oldState.getInputs().get(inputFile);
      if (oldInput == null || oldInput.isProcessingRequired()) {
        // this is new or changed input file
        result.add(registerInput(inputFile));
      }
    }

    return result;
  }

  // low-level methods

  /**
   * @return
   * @throws IOException if a stale output file cannot be deleted.
   */
  public Iterable<DefaultOutput> deleteStaleOutputs() throws IOException {
    List<DefaultOutput> deleted = new ArrayList<DefaultOutput>();

    oldOutpus: for (Map.Entry<File, DefaultOutput> oldOutput : oldState.getOutputs().entrySet()) {
      final File outputFile = oldOutput.getKey();

      // keep if output file was registered during this build
      if (outputs.containsKey(outputFile)) {
        continue oldOutpus;
      }

      for (DefaultInput oldInput : oldOutput.getValue().getAssociatedInputs()) {
        final File inputFile = oldInput.getResource();

        if (inputFile.canRead()) {
          // keep if inputFile is not registered during this build
          // keep if inputFile is registered and is associated with outputFile

          final DefaultInput input = inputs.get(inputFile);
          if (input == null || input.isAssociatedOutput(outputFile)) {
            continue oldOutpus;
          }
        }
      }

      if (outputFile.exists() && !outputFile.delete()) {
        throw new IOException("Could not delete file " + outputFile);
      }

      deleted.add(oldOutput.getValue());
    }
    return deleted;
  }

  /**
   * Returns {@code Input}s with this requirement
   */
  public Iterable<DefaultInput> getDependencies(String qualifier, Serializable localName) {
    Set<DefaultInput> result = requirementInputs.get(new QualifiedName(qualifier, localName));
    return result != null ? result : Collections.<DefaultInput>emptyList();
  }

  @Override
  public DefaultOutput registerOutput(File outputFile) {
    outputFile = normalize(outputFile);

    DefaultOutput output = outputs.get(outputFile);
    if (output == null) {
      outputs.putIfAbsent(outputFile, new DefaultOutput(this, outputFile));
      output = outputs.get(outputFile);
    }

    return output;
  }

  @Override
  public DefaultOutput getOldOutput(File outputFile) {
    return oldState.getOutputs().get(outputFile);
  }

  @Override
  public DefaultInput registerInput(File inputFile) {
    if (!inputFile.canRead()) {
      throw new IllegalArgumentException("Input file does not exist or cannot be read " + inputFile);
    }

    inputFile = normalize(inputFile);

    DefaultInput result = inputs.get(inputFile);
    if (result == null) {
      // XXX do I do this right? need to check with the concurrency book
      inputs.putIfAbsent(inputFile, new DefaultInput(this, inputFile));
      result = inputs.get(inputFile);
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

  @Override
  public FileSetBuilder fileSetBuilder() {
    // TODO Auto-generated method stub
    return null;
  }

  // to throw
  // public abstract void commit() throws E;

  // association management

  @Override
  public DefaultOutput associateOutput(DefaultInput input, File outputFile) {
    DefaultOutput output = registerOutput(outputFile);
    associate(input, output);
    return output;
  }

  @Override
  public void associate(DefaultInput input, DefaultOutput output) {
    File inputFile = input.getResource();
    Collection<DefaultOutput> outputs = inputOutputs.get(inputFile);
    if (outputs == null) {
      inputOutputs.putIfAbsent(inputFile, new LinkedHashSet<DefaultOutput>());
      outputs = inputOutputs.get(inputFile);
    }
    outputs.add(output); // XXX NOT THREAD SAFE
  }

  @Override
  public boolean isAssociatedOutput(DefaultInput input, File outputFile) {
    DefaultOutput output = outputs.get(outputFile);
    if (output == null) {
      return false;
    }
    Collection<DefaultOutput> outputs = inputOutputs.get(input.getResource());
    return outputs != null && outputs.contains(output);
  }

  @Override
  public Collection<DefaultInput> getAssociatedInputs(DefaultOutput output) {
    return Collections.unmodifiableCollection(outputInputs.get(output.getResource()));
  }

  @Override
  public void associateIncludedInput(DefaultInput input, File includedFile) {
    File inputFile = input.getResource();
    Collection<File> includedFiles = inputIncludedInputs.get(inputFile);
    if (includedFiles == null) {
      inputIncludedInputs.putIfAbsent(inputFile, new LinkedHashSet<File>());
      includedFiles = inputIncludedInputs.get(inputFile);
    }
    includedFiles.add(includedFile); // XXX NOT THREAD SAFE
  }

  @Override
  public boolean isProcessingRequired(DefaultInput input) {
    return true;
  }
}
