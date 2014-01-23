package io.takari.incremental;

import java.io.File;
import java.io.OutputStream;



public interface BuildContext {

  public static interface Input {
    public void addIncludedInput(File file);

    public Output registerOutput(File file);
  }

  public static interface Output {
    public OutputStream newOutputStream();
  }

  public Input registerInputForProcessing(File file);

  public Iterable<? extends Input> registerInputsForProcessing(FileSet fileSet);

}
