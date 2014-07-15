package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext.Severity;

import java.io.Serializable;

class Message implements Serializable {

  private static final long serialVersionUID = 7798138299696868415L;

  public final int line;

  public final int column;

  public final String message;

  public final Severity severity;

  public final Throwable cause;

  private final int hashCode;

  public Message(int line, int column, String message, Severity severity, Throwable cause) {
    this.line = line;
    this.column = column;
    this.message = message;
    this.severity = severity;
    this.cause = cause;
    this.hashCode = _hashCode();
  }

  private int _hashCode() {
    int result = 31;
    result = result * 17 + line;
    result = result * 17 + column;
    result = result * 17 + (message != null ? message.hashCode() : 0);
    result = result * 17 + (severity != null ? severity.hashCode() : 0);
    result = result * 17 + (cause != null ? cause.hashCode() : 0);
    return result;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof Message)) {
      return false;
    }

    Message other = (Message) obj;

    return line == other.line && column == other.column && eq(message, other.message)
        && eq(severity, other.severity) && eq(cause, other.cause);
  }

  private static <T> boolean eq(T a, T b) {
    return a != null ? a.equals(b) : b == null;
  }
}
