/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import java.lang.annotation.Annotation;

public abstract class AbstractFileParameter<A extends Annotation> extends AbstractParameter {

    protected final A annotation;

    protected AbstractFileParameter(MemberAdapter element, TypeAdapter type, Class<A> annotationClass) {
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
