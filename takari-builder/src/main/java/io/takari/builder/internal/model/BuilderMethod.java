/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import io.takari.builder.Builder;
import io.takari.builder.NonDeterministic;

public class BuilderMethod {

    private final MethodAdapter element;
    private final Builder annotation;
    private final TypeAdapter declaringType;
    private final boolean nonDeterministic;

    public BuilderMethod(MethodAdapter element, TypeAdapter declaringType) {
        this.element = element;
        this.annotation = element != null ? element.getAnnotation(Builder.class) : null;
        this.nonDeterministic = element != null && element.getAnnotation(NonDeterministic.class) != null ? true : false;
        this.declaringType = declaringType;
    }

    public MethodAdapter originatingElement() {
        return element;
    }

    public Builder annotation() {
        return annotation;
    }

    public boolean isNonDeterministic() {
        return nonDeterministic;
    }

    public TypeAdapter declaringType() {
        return declaringType;
    }

    public void accept(BuilderMetadataVisitor visitor) {
        visitor.visitBuilder(this);
    }
}
