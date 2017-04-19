package io.takari.builder;

public class BuilderContext {

  private BuilderContext() {
    throw new UnsupportedOperationException();
  }

  public static Messages getMessages() {
    return io.takari.builder.internal.BuilderContext.MESSAGES;
  }

}
