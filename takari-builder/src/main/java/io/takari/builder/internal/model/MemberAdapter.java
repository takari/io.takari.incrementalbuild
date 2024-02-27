/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import java.lang.annotation.Annotation;
import java.util.List;

public interface MemberAdapter {
    boolean isAnnotationPresent(Class<? extends Annotation> annotationClass);

    <T extends Annotation> T getAnnotation(Class<T> annotationClass);

    String getName();

    /**
     * Returns type that declares this member.
     */
    TypeAdapter getDeclaringType();

    /**
     * Returns element type of this multi-value member.
     *
     * @throws IllegalArgumentException if the member is not multivalue.
     */
    List<TypeAdapter> getParameterTypes();

    /**
     * Returns this member type.
     */
    TypeAdapter getType();
}
