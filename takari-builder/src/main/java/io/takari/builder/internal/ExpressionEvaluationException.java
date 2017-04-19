package io.takari.builder.internal;

@SuppressWarnings("serial")
public class ExpressionEvaluationException extends Exception {

  public ExpressionEvaluationException(String message, Exception e) {
    super(message, e);
  }

  public ExpressionEvaluationException(String message) {
    super(message);
  }

}
