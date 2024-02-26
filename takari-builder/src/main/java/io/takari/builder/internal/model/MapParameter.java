/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import io.takari.builder.Parameter;

public class MapParameter extends AbstractParameter {

    private final Parameter annotation;

    /**
     * @param element is the originating element of the map parameter
     * @param type is the type of the originating element
     */
    public MapParameter(MemberAdapter element, TypeAdapter type) {
        super(element, type);
        this.annotation = element.getAnnotation(Parameter.class);
    }

    @Override
    public Parameter annotation() {
        return annotation;
    }

    @Override
    public boolean required() {
        return annotation != null && annotation.required();
    }

    @Override
    public void accept(BuilderMetadataVisitor visitor) {
        visitor.visitMap(this);
    }
}
