package io.takari.builder.enforcer.modularity.internal;

@SuppressWarnings("serial")
public class BasedirViolationException extends RuntimeException {

  public BasedirViolationException(String message) {
    super(message);
  }
}
