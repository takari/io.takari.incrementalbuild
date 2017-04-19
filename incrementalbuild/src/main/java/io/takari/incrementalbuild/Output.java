package io.takari.incrementalbuild;

import java.io.IOException;
import java.io.OutputStream;

public interface Output<T> extends Resource<T> {

  public OutputStream newOutputStream() throws IOException;

}
