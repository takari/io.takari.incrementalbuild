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

  public class SimpleInputMetadata<R> implements InputMetadata<R> {

    private R resource;

    public SimpleInputMetadata(R file) {
      this.resource = file;
    }

    @Override
    public R getResource() {
      return resource;
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
    @SuppressWarnings("unchecked")
    public Input<R> process() {
      if (resource instanceof File) {
        SimpleInput<File> input = new SimpleInput<File>((File) resource);
        processedInputs.put((File) resource, input);
        return (Input<R>) input;
      }
      return new SimpleInput<R>(resource);
    }
  }

  public class SimpleInput<R> implements Input<R> {

    private final R resource;

    private final Map<String, Serializable> properties = new HashMap<String, Serializable>();

    public SimpleInput(R resource) {
      this.resource = resource;
    }

    @Override
    public R getResource() {
      return resource;
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
    public Input<R> process() {
      return this;
    }

    @Override
    public ResourceStatus getStatus() {
      return ResourceStatus.NEW;
    }

    @Override
    public void associateIncludedInput(File included) {}

    @Override
    public Output<File> associateOutput(Output<File> output) {
      return output;
    }

    @Override
    public Output<File> associateOutput(File outputFile) {
      return new SimpleOutput(outputFile);
    }

    @Override
    public <V extends Serializable> Serializable setValue(String key, V value) {
      return properties.put(key, value);
    }

    @Override
    public void addMessage(int line, int column, String message, Severity severity, Throwable cause) {}
  }

  public class SimpleOutput implements Output<File> {

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
    public <I> Iterable<? extends InputMetadata<I>> getAssociatedInputs(Class<I> clazz) {
      return Collections.emptyList();
    }

    @Override
    public <I> void associateInput(InputMetadata<I> input) {}

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

  private final Map<File, SimpleInput<File>> processedInputs =
      new LinkedHashMap<File, SimpleInput<File>>();

  private final Map<File, SimpleOutput> processedOutputs = new LinkedHashMap<File, SimpleOutput>();

  @Override
  public InputMetadata<File> registerInput(File inputFile) {
    registeredInputs.add(inputFile);
    return new SimpleInputMetadata<File>(inputFile);
  }

  public <R> InputMetadata<R> registerInput(R inputResource) {
    return new SimpleInputMetadata<R>(inputResource);
  }

  @Override
  public Iterable<? extends InputMetadata<File>> registerInputs(Iterable<File> inputFiles) {
    Set<SimpleInputMetadata<File>> result = new LinkedHashSet<SimpleInputMetadata<File>>();
    for (File inputFile : inputFiles) {
      registeredInputs.add(inputFile);
      result.add(new SimpleInputMetadata<File>(inputFile));
    }
    return result;
  }

  @Override
  public Iterable<? extends Input<File>> registerAndProcessInputs(Iterable<File> inputFiles) {
    Set<SimpleInput<File>> result = new LinkedHashSet<SimpleInput<File>>();
    for (File inputFile : inputFiles) {
      if (!processedInputs.containsKey(inputFile)) {
        SimpleInput<File> input = new SimpleInput<File>(inputFile);
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
  public Iterable<? extends InputMetadata<File>> getRegisteredInputs() {
    Set<InputMetadata<File>> result = new LinkedHashSet<InputMetadata<File>>();
    for (File inputFile : registeredInputs) {
      InputMetadata<File> input = processedInputs.get(inputFile);
      if (input == null) {
        input = new SimpleInput<File>(inputFile);
      }
      result.add(input);
    }
    return result;
  }

  @Override
  public Iterable<? extends OutputMetadata<File>> getProcessedOutputs() {
    Set<OutputMetadata<File>> result = new LinkedHashSet<OutputMetadata<File>>();
    for (Output<File> output : processedOutputs.values()) {
      result.add(output);
    }
    return result;
  }
}
