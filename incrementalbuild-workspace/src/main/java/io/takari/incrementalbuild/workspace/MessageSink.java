package io.takari.incrementalbuild.workspace;


public interface MessageSink {
  public static enum Severity {
    ERROR, WARNING, INFO
  }

  public void clearMessages(Object resource);

  public void message(Object resource, int line, int column, String message, Severity severity,
      Throwable cause);

}
