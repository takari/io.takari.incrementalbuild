package io.takari.incremental;

import java.io.File;
import java.io.OutputStream;
import java.io.Serializable;



public interface BuildContext {

  public static final int SEVERITY_ERROR = 1;

  public static final int SEVERITY_WARNING = 2;

  public static interface Input {
    public void addIncludedInput(File file);

    public Output registerOutput(File file);

    public boolean isProcessingRequired();

    public File getResource();

    public <T extends Serializable> void setValue(String key, T value);

    public <T extends Serializable> T getValue(String key, Class<T> clazz);

    public void addMessage(int line, int column, String message, int severity, Throwable cause);

    // the following is required to support include inputs
    // public void addMessage(Input includedInput, int line, int column, String message, int
    // severity,
    // Throwable cause);
  }

  public static interface Output {
    public OutputStream newOutputStream();

    public Iterable<? extends Input> getRegisteredInputs();

    public void addInput(Input input);
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
  public Input registerInputForProcessing(File file);

  public Iterable<? extends Input> registerInputsForProcessing(FileSet fileSet);

  public Output registerOutput(File file);

  public Output getOldOutput(File file);

  public Input registerInput(File file);

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
