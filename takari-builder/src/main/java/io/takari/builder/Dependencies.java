package io.takari.builder;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Project dependencies.
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface Dependencies {
  ResolutionScope scope();

  boolean transitive() default true;
}
