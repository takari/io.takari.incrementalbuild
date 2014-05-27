package io.takari.incrementalbuild.workspace;


public interface MessageSink {
  public static enum Severity {
    ERROR, WARNING, INFO
  }

  public void message(Object resource, int line, int column, String message, Severity severity,
      Throwable cause);

  public MessageSink replayMessageSink();

  public int getErrorCount();

  public void clearMessages(Object resource);
}
