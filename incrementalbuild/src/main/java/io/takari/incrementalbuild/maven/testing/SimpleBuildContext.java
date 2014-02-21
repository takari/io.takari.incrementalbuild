package io.takari.incrementalbuild.maven.testing;

import io.takari.incrementalbuild.BuildContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simple BuildContext implementation that does not retain any state. Useful as a mock BuildContext
 * implementation for unit test purposes.
 */
public class SimpleBuildContext implements BuildContext {

  private class SimpleInputMetadata implements InputMetadata<File> {

    private File file;

    public SimpleInputMetadata(File file) {
      this.file = file;
    }

    @Override
    public File getResource() {
      return file;
    }

    @Override
    public ResourceStatus getStatus() {
      return ResourceStatus.NEW;
    }

    @Override
    public Iterable<? extends OutputMetadata<File>> getAssociatedOutputs() {
      return Collections.emptyList();
    }

    @Override
    public <V extends Serializable> V getValue(String key, Class<V> clazz) {
      return null;
    }

    @Override
    public Input<File> process() {
      SimpleInput input = new SimpleInput(file);
      processedInputs.put(file, input);
      return input;
    }
  }

  private class SimpleInput implements Input<File> {

    private final File file;

    private final Map<String, Serializable> properties = new HashMap<String, Serializable>();

    public SimpleInput(File file) {
      this.file = file;
    }

    @Override
    public File getResource() {
      return file;
    }

    @Override
    public Iterable<? extends OutputMetadata<File>> getAssociatedOutputs() {
      return null;
    }

    @Override
    public <V extends Serializable> V getValue(String key, Class<V> clazz) {
      return clazz.cast(properties.get(key));
    }

    @Override
    public Input<File> process() {
      return this;
    }

    @Override
    public ResourceStatus getStatus() {
      return ResourceStatus.NEW;
    }

    @Override
    public void associateIncludedInput(File resource) {}

    @Override
    public Output<File> associateOutput(File outputFile) {
      return new SimpleOutput(outputFile);
    }

    @Override
    public <V extends Serializable> Serializable setValue(String key, V value) {
      return properties.put(key, value);
    }

    @Override
    public void addMessage(int line, int column, String message, int severity, Throwable cause) {}
  }

  private class SimpleOutput implements Output<File> {

    private final File file;

    private final Map<String, Serializable> properties = new HashMap<String, Serializable>();

    public SimpleOutput(File file) {
      this.file = file;
    }

    @Override
    public File getResource() {
      return file;
    }

    @Override
    public ResourceStatus getStatus() {
      return ResourceStatus.NEW;
    }

    @Override
    public OutputStream newOutputStream() throws IOException {
      return new FileOutputStream(file);
    }

    @Override
    public Iterable<? extends Input<File>> getAssociatedInputs() {
      return Collections.emptyList();
    }

    @Override
    public void associateInput(InputMetadata<File> input) {}

    @Override
    public <V extends Serializable> Serializable setValue(String key, V value) {
      return properties.put(key, value);
    }

    @Override
    public <V extends Serializable> V getValue(String key, Class<V> clazz) {
      return clazz.cast(properties.get(key));
    }
  }

  private final Set<File> registeredInputs = new LinkedHashSet<File>();

  private final Map<File, SimpleInput> processedInputs = new LinkedHashMap<File, SimpleInput>();

  private final Map<File, SimpleOutput> processedOutputs = new LinkedHashMap<File, SimpleOutput>();

  @Override
  public InputMetadata<File> registerInput(File inputFile) {
    registeredInputs.add(inputFile);
    return new SimpleInputMetadata(inputFile);
  }

  @Override
  public Iterable<? extends InputMetadata<File>> registerInputs(Iterable<File> inputFiles) {
    Set<SimpleInputMetadata> result = new LinkedHashSet<SimpleInputMetadata>();
    for (File inputFile : inputFiles) {
      registeredInputs.add(inputFile);
      result.add(new SimpleInputMetadata(inputFile));
    }
    return result;
  }

  @Override
  public Iterable<? extends Input<File>> registerAndProcessInputs(Iterable<File> inputFiles) {
    Set<SimpleInput> result = new LinkedHashSet<SimpleInput>();
    for (File inputFile : inputFiles) {
      if (!processedInputs.containsKey(inputFile)) {
        SimpleInput input = new SimpleInput(inputFile);
        registeredInputs.add(inputFile);
        processedInputs.put(inputFile, input);
        result.add(input);
      }
    }
    return result;
  }

  @Override
  public Output<File> processOutput(File outputFile) {
    SimpleOutput output = processedOutputs.get(outputFile);
    if (output == null) {
      output = new SimpleOutput(outputFile);
      processedOutputs.put(outputFile, output);
    }
    return output;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Iterable<? extends InputMetadata<T>> getRegisteredInputs(Class<T> clazz) {
    Set<InputMetadata<T>> result = new LinkedHashSet<InputMetadata<T>>();
    for (File inputFile : registeredInputs) {
      InputMetadata<T> input = (InputMetadata<T>) processedInputs.get(inputFile);
      if (input == null) {
        input = (InputMetadata<T>) new SimpleInput(inputFile);
      }
      result.add(input);
    }
    return result;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> Iterable<? extends OutputMetadata<T>> getProcessedOutputs(Class<T> clazz) {
    Set<OutputMetadata<T>> result = new LinkedHashSet<OutputMetadata<T>>();
    for (Output<File> output : processedOutputs.values()) {
      result.add((OutputMetadata<T>) output);
    }
    return result;
  }
}
