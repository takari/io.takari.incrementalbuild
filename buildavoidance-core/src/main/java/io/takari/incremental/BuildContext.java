package io.takari.incremental;

import java.io.File;
import java.io.OutputStream;
import java.io.Serializable;



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
    public OutputStream newOutputStream();

    public Iterable<? extends Input<T>> getAssociatedInputs();

    public void associateInput(Input<T> input);
  }

  /**
   * Convenience method fully equivalent to
   * 
   * <code>
   *     FileSet fileSet = context.fileSetBuild().fromFile(file);
   *     Iterator&lt;Input> iterator = context.registerInputsForProcessing(fileSet).iterator();
   *     Input input = iterator.hasNext()? iterator.next(): null;
   * </code>
   * 
   * @return registered input or {@code null} if input file does not require processing
   */
  public Input<File> processInput(File file);

  public Iterable<? extends Input<File>> processInputs(FileSet fileSet);

  public Output<File> registerOutput(File file);

  public Output<File> getOldOutput(File file);

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
