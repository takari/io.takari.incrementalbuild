package io.takari.builder.internal;

import java.lang.annotation.Annotation;

public interface BuilderField {
  public boolean hasAnnotation(Class<? extends Annotation> annotationClass);
  public String getDeclaringClassname();
  public String getName();
  <A extends Annotation> A getAnnotation(Class<A> annotationType);
  public boolean isMultivalueFieldType();
  public boolean isTypeAssignableFrom(Class<?> clazz);
  public boolean isElementTypeAssignableFrom(Class<?> clazz);
  public boolean isPrimitiveType();
  public String getJavadoc();
}
