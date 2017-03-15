package io.takari.builder;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that annotated element is an output file.
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface OutputFile {
  /**
   * @see Parameter#required()
   */
  boolean required() default true;

  /**
   * @see Parameter#value()
   */
  String[] value() default {};

  /**
   * @see Parameter#defaultValue()
   */
  String[] defaultValue() default {};
}
