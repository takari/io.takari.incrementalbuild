package io.takari.builder;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that annotated element is a builder parameter.
 */
// TODO clarify what types are supported
@Target({FIELD})
@Retention(RUNTIME)
public @interface Parameter {

  /**
   * If the parameter value is required or can be omitted. Primitive type parameters are always
   * required. Reference type parameters will be assigned value {@code null} if their value is
   * omitted.
   */
  boolean required() default true;

  /**
   * Parameter value. Cannot be changed/overridden in pom.xml {@code <configuration>} section.
   * Useful with parameter ${expressions}, but static value can be used too. #value and
   * #defaultValue cannot be both used for the same parameter.
   * 
   * @see #defaultValue()
   */
  String[] value() default {};

  /**
   * Parameter default value. Can be changed/overridden in pom.xml {@code <configuration>} section.
   * Both parameter ${expressions} and static values are allowed. #value and #defaultValue cannot be
   * both used for the same parameter.
   * 
   * @see #value()
   */
  String[] defaultValue() default {};

}
