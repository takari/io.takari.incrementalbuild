package io.takari.incremental;

import java.io.File;
import java.io.OutputStream;



public interface BuildContext {

  public static interface Input {
    public void addIncludedInput(File file);

    public Output registerOutput(File file);

    public boolean isProcessingRequired();

    public File getResource();
  }

  public static interface Output {
    public OutputStream newOutputStream();

    public Iterable<? extends Input> getRegisteredInputs();

    public void addInput(Input input);
  }

  public Input registerInputForProcessing(File file);

  public Iterable<? extends Input> registerInputsForProcessing(FileSet fileSet);

  public Output registerOutput(File file);

  public Output getOldOutput(File file);

}
