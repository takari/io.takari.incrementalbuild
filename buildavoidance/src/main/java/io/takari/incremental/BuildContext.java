package io.takari.incremental;

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

  public static interface Input<T> {
    public void associateIncludedInput(T resource);

    public Output<T> associateOutput(T resource);

    // processing of deleted inputs is NOT required
    // TODO this method only applies to old state, see if you can remove it somehow
    public boolean isProcessingRequired();

    public T getResource();

    public <V extends Serializable> void setValue(String key, V value);

    public <V extends Serializable> V getValue(String key, Class<V> clazz);

    public void addMessage(int line, int column, String message, int severity, Throwable cause);

    // the following is required to support include inputs
    // public void addMessage(Input includedInput, int line, int column, String message, int
    // severity,
    // Throwable cause);
  }

  public static interface Output<T> {
    public T getResource();

    public OutputStream newOutputStream() throws IOException;

    public Iterable<? extends Input<T>> getAssociatedInputs();

    public void associateInput(Input<T> input);
  }

  /**
   * Registers specified input {@code File} with this build context.
   * <p>
   * This method can be invoked multiple times for the same input file. During the first invocation
   * internal data structures corresponding to the input file are initialized with clean state, i.e.
   * no associated output files, no message, etc. During subsequent invocations the internal data
   * structures are not modified.
   * <p>
   * Invocation of this method forces processing of the registered input file by build avoidance
   * framework even if the input file has not changed since last build.
   * 
   * @return {@link Input} representing the input file, never {@code null}.
   * @throws IllegalArgumentException if inputFile does not exist or cannot be read
   */
  public Input<File> registerInput(File inputFile);

  /**
   * 
   * XXX decide if the same input should be "processed" multiple times or not. Tentatively, the same
   * input is only processed once.
   * 
   * @return registered input or {@code null} if the input file does not require processing
   * @throws IllegalArgumentException if inputFile does not exist or cannot be read
   */
  public Input<File> processInput(File inputFile);

  /**
   * XXX decide if the same input should be "processed" multiple times or not. Tentatively, the same
   * input is only processed once.
   */
  public Iterable<? extends Input<File>> processInputs(Iterable<File> inputFiles);

  public Output<File> registerOutput(File outputFile);

  public Output<File> getOldOutput(File outputFile);

}
