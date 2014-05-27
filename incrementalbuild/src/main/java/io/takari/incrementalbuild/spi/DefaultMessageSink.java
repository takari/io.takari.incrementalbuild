package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.workspace.MessageSink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMessageSink implements MessageSink {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private int errorCount;

  @Override
  public void message(Object resource, int line, int column, String message, Severity severity,
      Throwable cause) {
    if (severity == Severity.ERROR) {
      errorCount++;
      log.error("{}:[{}:{}] {}", resource.toString(), line, column, message, cause);
    } else {
      log.warn("{}:[{}:{}] {}", resource.toString(), line, column, message, cause);
    }
  }

  @Override
  public MessageSink replayMessageSink() {
    log.info("Replaying recorded messages...");

    return this;
  }

  @Override
  public int getErrorCount() {
    return errorCount;
  }

  @Override
  public void clearMessages(Object resource) {}
}
