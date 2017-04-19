package io.takari.builder.internal;

import static io.takari.builder.internal.Message.MessageSeverity.ERROR;
import static io.takari.builder.internal.Message.MessageSeverity.INFO;
import static io.takari.builder.internal.Message.MessageSeverity.WARNING;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import io.takari.builder.Messages;
import io.takari.builder.internal.BuilderRunner.ExceptionFactory;

// TODO consider merging with BuilderContext
class MessageCollector implements Messages {
  private final Logger log;

  // maintain overall message order because root cause is often (but not always)
  // reported before any secondary problems
  // NB: all access must synchronize on the instance
  private final Set<Message> messages = new LinkedHashSet<>();

  public MessageCollector(Logger log) {
    this.log = log;
  }

  private void collectAndLog(Message message) {
    collect(message);
    log(message);
  }

  private void collect(Message message) {
    synchronized (messages) {
      messages.add(message);
    }
  }

  // copy&paste from io.takari.incrementalbuild.spi.AbstractBuildContext.log
  private void log(Message message) {
    String resource = message.file;
    switch (message.severity) {
      case ERROR:
        log.error("{}:[{},{}] {}", resource, message.line, message.column,
            message.message, message.cause);
        break;
      case WARNING:
        log.warn("{}:[{},{}] {}", resource, message.line, message.column,
            message.message, message.cause);
        break;
      default:
        log.info("{}:[{},{}] {}", resource, message.line, message.column,
            message.message, message.cause);
        break;
    }
  }

  List<Message> getCollectedMessages() {
    synchronized (messages) {
      // copy so clients don't have to synchronize
      return new ArrayList<>(messages);
    }
  }

  <E extends Exception> void replayMessages(ExceptionFactory<E> efactory,
      final List<Message> messages) throws E {
    messages.forEach(message -> collect(message));
    throwExceptionIfThereWereErrorMessages(efactory);
  }

  <E extends Exception> void throwExceptionIfThereWereErrorMessages(ExceptionFactory<E> efactory)
      throws E {
    synchronized (messages) {
      assertBuildSuccess(efactory, messages);
    }
  }

  private static <E extends Exception> void assertBuildSuccess(ExceptionFactory<E> efactory,
      Collection<Message> messages) throws E {
    int errorCount = 0;
    StringBuilder errors = new StringBuilder();
    for (Message message : messages) {
      if (message.severity == Message.MessageSeverity.ERROR) {
        errorCount++;
        errors.append(String.format("%s:[%d:%d] %s\n", message.file.toString(), message.line,
            message.column, message.message));
      }
    }
    if (errorCount > 0) {
      throw efactory.exception(errorCount + " error(s) encountered:\n" + errors.toString(), null);
    }
  }

  //
  // Messages API implementation
  //


  @Override
  public void info(File resource, int line, int column, String message, Throwable cause) {
    collectAndLog(new Message(resource.toString(), line, column, message, INFO, cause));
  }

  @Override
  public void warn(File resource, int line, int column, String message, Throwable cause) {
    collectAndLog(new Message(resource.toString(), line, column, message, WARNING, cause));
  }

  @Override
  public void error(File resource, int line, int column, String message, Throwable cause) {
    collectAndLog(new Message(resource.toString(), line, column, message, ERROR, cause));
  }

  @Override
  public void info(Path resource, int line, int column, String message, Throwable cause) {
    collectAndLog(new Message(resource.toString(), line, column, message, INFO, cause));
  }

  @Override
  public void warn(Path resource, int line, int column, String message, Throwable cause) {
    collectAndLog(new Message(resource.toString(), line, column, message, WARNING, cause));
  }

  @Override
  public void error(Path resource, int line, int column, String message, Throwable cause) {
    collectAndLog(new Message(resource.toString(), line, column, message, ERROR, cause));
  }

}
