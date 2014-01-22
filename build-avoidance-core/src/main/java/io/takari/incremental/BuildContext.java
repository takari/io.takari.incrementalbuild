package io.takari.incremental;

import java.io.File;
import java.io.OutputStream;



public interface BuildContext {

  public static interface Input {
    public boolean requireProcessing();

    public Output registerOutput(File file);
  }

  public static interface Output {

    public OutputStream newOutputStream();
  }

  public Input registerInput(File file);

}
