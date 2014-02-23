package io.takari.incrementalbuild;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;



/**
 * 
 * XXX decide if implementations of this interface should be thread-safe or not.
 * 
 * @author igor
 */
public interface BuildContext {

  public static final int SEVERITY_ERROR = 1;

  public static final int SEVERITY_WARNING = 2;

  public static enum ResourceStatus {

    /**
     * Input is new in this build, i.e. it was not present in the previous build.
     */
    NEW,

    /**
     * Input itself changed or any of its included inputs changed or was removed since last build.
     */
    MODIFIED,

    /**
     * Input itself and all includes inputs, if any, did not change since last build.
     */
    UNMODIFIED,

    /**
     * Input was removed since last build.
     */
    REMOVED;
  }

  /**
   * Read-only state associated with input. Use {@link #process()} to manipulate the state.
   */
  public static interface InputMetadata<T> {

    public T getResource();

    /**
     * Returns up-to-date status of this input compared to the previous build. Covers status of the
     * input itself, included inputs, if any, and associated outputs, if any. Honours context build
     * escalation.
     */
    public ResourceStatus getStatus();

    public Iterable<? extends OutputMetadata<File>> getAssociatedOutputs();

    public <V extends Serializable> V getValue(String key, Class<V> clazz);

    public Input<T> process();

  }

  /**
   * Read-write state associated with input.
   */
  public static interface Input<T> extends InputMetadata<T> {

    public InputMetadata<File> associateIncludedInput(File included);

    public Output<File> associateOutput(File outputFile);

    /**
     * Returns value associated with the key during previous build.
     */
    public <V extends Serializable> Serializable setValue(String key, V value);

    public void addMessage(int line, int column, String message, int severity, Throwable cause);

    // the following is required to support include inputs
    // public void addMessage(Input includedInput, int line, int column, String message, int
    // severity,
    // Throwable cause);
  }

  public static interface OutputMetadata<T> {
    public T getResource();

    /**
     * Returns up-to-date status of this output compared to the previous build. Does not consider
     * associated inputs.
     */
    public ResourceStatus getStatus();

    public <I> Iterable<? extends InputMetadata<I>> getAssociatedInputs(Class<I> clazz);

    public <V extends Serializable> V getValue(String key, Class<V> clazz);
  }

  public static interface Output<T> extends OutputMetadata<T> {

    public OutputStream newOutputStream() throws IOException;

    public <I> void associateInput(InputMetadata<I> input);

    public <V extends Serializable> Serializable setValue(String key, V value);
  }

  /**
   * Registers specified input {@code File} with this build context.
   * 
   * @return {@link InputMetadata} representing the input file, never {@code null}.
   * @throws IllegalArgumentException if inputFile does not exist or cannot be read
   * 
   * @see #processInput(File)
   * @see #processInputs(Iterable)
   */
  public InputMetadata<File> registerInput(File inputFile);

  public Iterable<? extends InputMetadata<File>> registerInputs(Iterable<File> inputFiles);

  public Iterable<? extends Input<File>> registerAndProcessInputs(Iterable<File> inputFiles);

  public Output<File> processOutput(File outputFile);

  /**
   * Returns all inputs registered with this {@link BuildContext} during current and previous
   * builds.
   */
  public Iterable<? extends InputMetadata<File>> getRegisteredInputs();

  /**
   * Returns all outputs processed by this {@link BuildContext} during current build or carried over
   * from previous build.
   */
  public Iterable<? extends OutputMetadata<File>> getProcessedOutputs();
}
