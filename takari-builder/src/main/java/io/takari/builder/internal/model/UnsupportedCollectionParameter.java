package io.takari.builder.internal.model;

import java.lang.annotation.Annotation;
import java.util.List;

public class UnsupportedCollectionParameter extends AbstractParameter {

  List<TypeAdapter> elementTypes;

  protected UnsupportedCollectionParameter(MemberAdapter element, TypeAdapter type,
      List<TypeAdapter> elementTypes) {
    super(element, type);
    this.elementTypes = elementTypes;
  }

  @Override
  public Annotation annotation() {
    return null;
  }

  @Override
  public boolean required() {
    return false;
  }

  @Override
  public void accept(BuilderMetadataVisitor visitor) {
    visitor.visitUnsupportedCollection(this);
  }

}
