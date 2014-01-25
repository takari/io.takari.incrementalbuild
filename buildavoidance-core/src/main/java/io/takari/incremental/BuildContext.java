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
    public void associateIncludedInput(T file);

    public Output<T> associateOutput(T file);

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
   * Convenience method fully equivalent to
   * 
   * <code>
   *     FileSet fileSet = context.fileSetBuild().fromFile(file);
   *     Iterator&lt;Input> iterator = context.processInputs(fileSet).iterator();
   *     Input input = iterator.hasNext()? iterator.next(): null;
   * </code>
   * 
   * @return registered input or {@code null} if the input file does not require processing
   */
  public Input<File> processInput(File file);

  public Iterable<? extends Input<File>> processInputs(FileSet fileSet);

  public Output<File> registerOutput(File file);

  public Output<File> getOldOutput(File file);

  /**
   * Registers specified input {@code File} with this build context.
   * <p>
   * The same {@link Input} instance is returned if this method is invoked multiple times during the
   * same build. During the first invocation {@link Input} is initialized with clean state, i.e. no
   * associated output files, no message, etc. The same initialized instance is returned as is
   * during subsequent invocations of this method.
   * <p>
   * Invocation of this method forces processing of the registered input file by build avoidance
   * framework even if the input file has not changed since last build.
   * <p>
   * XXX decide if this method returns the same or equal instance
   * 
   * @return {@link Input} representing the input file, or {@code null} if the input file does not
   *         exist or cannot be read.
   */
  public Input<File> registerInput(File file);

  /**
   * Returns new uninitialized {@link FileSetBuilder} instance.
   * <p>
   * Use of {@link FileSet}s is strongly recommended over direct filesystem scanning because it
   * provides potentially drastically better performance inside Eclipse workspace.
   * 
   * @return new file set builder
   */
  public FileSetBuilder fileSetBuilder();

}
