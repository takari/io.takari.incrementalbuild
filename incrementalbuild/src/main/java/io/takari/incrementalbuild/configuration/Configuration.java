package io.takari.incrementalbuild.configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional annotation that allows customization of how incremental build implementation handles
 * configuration parameters.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Configuration {
  /**
   * Indicates that annotated configuration parameter should be ignored by incremental build
   * implementation.
   */
  boolean ignored() default false;
}
