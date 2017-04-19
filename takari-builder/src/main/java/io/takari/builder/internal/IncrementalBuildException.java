package io.takari.builder.internal;

import java.io.IOException;

/**
 * Wraps {@link IOException} raises while writing builder execution undo log file.
 * 
 * Results in immediate ungraceful build execution termination.
 */
@SuppressWarnings("serial")
class IncrementalBuildException extends RuntimeException {
  public IncrementalBuildException(IOException cause) {
    super(cause);
  }
}
