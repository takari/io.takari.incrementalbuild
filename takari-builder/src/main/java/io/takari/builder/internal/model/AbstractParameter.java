/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import java.lang.annotation.Annotation;

public abstract class AbstractParameter {

    protected final MemberAdapter element;
    protected final TypeAdapter type;

    protected AbstractParameter(MemberAdapter element, TypeAdapter type) {
        this.element = element;
        this.type = type;
    }

    /**
     * Parameter originating model element.
     */
    public MemberAdapter originatingElement() {
        return element;
    }

    /**
     * Parameter annotation, can be {@code null}.
     */
    public abstract Annotation annotation();

    /**
     * Parameter target type. If originating model element type is an array or collection, then the
     * type is array/collection member type.
     */
    public TypeAdapter type() {
        return type;
    }

    /**
     * Returns {@code true} if this parameter much have configuration value provided in java or in
     * xml. Returns {@code false} is configuration value is not required for this parameter.
     */
    public abstract boolean required();

    /**
     * Parameter xml configuration element name.
     */
    public String name() {
        return element.getName();
    }

    public abstract void accept(BuilderMetadataVisitor visitor);

    @Override
    public String toString() {
        TypeAdapter declaringType = element.getDeclaringType();
        return declaringType.simpleName() + "." + name() + " [" + getClass().getSimpleName() + "]";
    }
}
