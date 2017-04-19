package io.takari.builder;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Indicates that annotated @{@link GeneratedResourcesDirectory} is a resource root with specified
 * includes/excludes parameters. Generated resources directories are added to MavenProject model as
 * a result of the build.
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface GeneratedResourcesDirectory {
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

  /**
   * Generated resource type. Cannot be changed/overridden in pom.xml {@code <configuration>} section.
   */
  ResourceType type() default ResourceType.MAIN;

  /**
   * A list of inclusion filters. Cannot be changed/overridden in pom.xml {@code <configuration>} section.
   * #includes and #defaultIncludes cannot be both used for the same parameter.
   */
  String[] includes() default {};

  /**
   * A list of default inclusion filters. Can be changed/overridden in pom.xml {@code <configuration>} section.
   * #includes and #defaultIncludes cannot be both used for the same parameter.
   */
  String[] defaultIncludes() default {};

  /**
   * A list of exclusion filters. Cannot be changed/overridden in pom.xml {@code <configuration>} section.
   * #excludes and #defaultExcludes cannot be both used for the same parameter.
   */
  String[] excludes() default {};

  /**
   * A list of default exclusion filters. Can be changed/overridden in pom.xml {@code <configuration>} section.
   * #excludes and #defaultExcludes cannot be both used for the same parameter.
   */
  String[] defaultExcludes() default {};
}
