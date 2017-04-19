package io.takari.builder.internal.model;

import io.takari.builder.Dependencies;

public class DependenciesParameter extends AbstractParameter {

  private final Dependencies annotation;
  private final TypeAdapter elementType;

  DependenciesParameter(MemberAdapter element, TypeAdapter type) {
    this(element, type, null);
  }

  DependenciesParameter(MemberAdapter element, TypeAdapter type, TypeAdapter elementType) {
    super(element, type);
    this.annotation = element.getAnnotation(Dependencies.class);
    this.elementType = elementType;
  }

  @Override
  public Dependencies annotation() {
    return annotation;
  }

  @Override
  public boolean required() {
    return false; // no configuration is required
  }

  public boolean transitive() {
    return annotation.transitive();
  }

  public TypeAdapter elementType() {
    return elementType;
  }


  @Override
  public void accept(BuilderMetadataVisitor visitor) {
    visitor.visitDependencies(this);
  }

}
