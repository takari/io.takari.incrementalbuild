package io.takari.builder.internal.model;

import java.lang.annotation.Annotation;

public abstract class AbstractFileParameter<A extends Annotation> extends AbstractParameter {

  protected final A annotation;

  protected AbstractFileParameter(MemberAdapter element, TypeAdapter type,
      Class<A> annotationClass) {
    super(element, type);
    this.annotation = element.getAnnotation(annotationClass);
  }

  @Override
  public A annotation() {
    return annotation;
  }

  public abstract String[] value();

  public abstract String[] defaultValue();
}
