package io.takari.builder.internal.model;

import io.takari.builder.DependencyResources;

public class DependencyResourcesParameter extends AbstractResourceSelectionParameter {

  private static final String[] EMPTY = new String[0];

  private final DependencyResources annotation;

  public DependencyResourcesParameter(MemberAdapter element, TypeAdapter type) {
    super(element, type);
    this.annotation = element.getAnnotation(DependencyResources.class);
  }

  @Override
  public DependencyResources annotation() {
    return annotation;
  }

  @Override
  public boolean required() {
    return annotation.resourcesRequired();
  }

  @Override
  public void accept(BuilderMetadataVisitor visitor) {
    visitor.visitDependencyResources(this);
  }

  @Override
  public String[] value() {
    return EMPTY;
  }

  @Override
  public String[] defaultValue() {
    return EMPTY;
  }

  @Override
  public String[] includes() {
    return annotation.includes();
  }

  @Override
  public String[] defaultIncludes() {
    return annotation.defaultIncludes();
  }

  @Override
  public String[] excludes() {
    return annotation.excludes();
  }

  @Override
  public String[] defaultExcludes() {
    return annotation.defaultExcludes();
  }

}
