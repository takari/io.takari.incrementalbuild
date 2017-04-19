package io.takari.builder;

/**
 * Dependency resolution scope
 */
public enum ResolutionScope {
  // apt-processor assumes names match org.apache.maven.plugins.annotations.ResolutionScope
  
  COMPILE_PLUS_RUNTIME,
  COMPILE,
  RUNTIME,
  TEST,
}
