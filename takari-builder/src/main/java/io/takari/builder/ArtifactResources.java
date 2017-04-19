package io.takari.builder;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({FIELD})
@Retention(RUNTIME)
public @interface ArtifactResources {
  ResolutionScope scope();

  /**
   * If {@code resourcesRequired=true}, the injected parameter value will be an artifact that has
   * matching resources.
   *
   * <p>
   * If {@code resourcesRequired=false}, the injected parameter value may be {@code null}, be an
   * artifact that does or does not have matching resources.
   */
  boolean resourcesRequired() default false;

  /**
   * Ant-like resource name includes patterns. If specified, cannot be changed/overridden in pom.xml
   * {@code <configuration>} section.
   */
  String[] includes() default {};

  /**
   * Ant-like resource name default includes patterns.
   */
  String[] defaultIncludes() default {};

  /**
   * Ant-like resource name excludes patterns. No resources are excluded by default. If specified,
   * cannot be changed/overridden in pom.xml {@code <configuration>} section.
   */
  String[] excludes() default {};

  String[] defaultExcludes() default {};

}
