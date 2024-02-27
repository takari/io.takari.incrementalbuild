/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
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
