package io.takari.builder.internal.model;

import java.lang.annotation.Annotation;

public interface MethodAdapter {

  <T extends Annotation> T getAnnotation(Class<T> annotationClass);

  int getParameterCount();
}
