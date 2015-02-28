package io.takari.incrementalbuild;

import java.io.File;

public interface Resource<T> extends ResourceMetadata<T> {

  public void addMessage(int line, int column, String message, MessageSeverity severity,
      Throwable cause);

  public Output<File> associateOutput(Output<File> output);

  public Output<File> associateOutput(File outputFile);
}
