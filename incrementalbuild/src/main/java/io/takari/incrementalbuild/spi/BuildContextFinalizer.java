package io.takari.incrementalbuild.spi;

public interface BuildContextFinalizer {
  public void registerContext(AbstractBuildContext context);
}
