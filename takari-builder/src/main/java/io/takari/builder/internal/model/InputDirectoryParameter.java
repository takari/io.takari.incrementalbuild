/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import io.takari.builder.InputDirectory;

public class InputDirectoryParameter extends AbstractParameter {

    private final InputDirectory annotation;

    InputDirectoryParameter(MemberAdapter element, TypeAdapter type) {
        super(element, type);
        this.annotation = element.getAnnotation(InputDirectory.class);
    }

    @Override
    public InputDirectory annotation() {
        return annotation;
    }

    @Override
    public boolean required() {
        return annotation.filesRequired();
    }

    public String[] value() {
        return annotation.value();
    }

    public String[] defaultValue() {
        return annotation.defaultValue();
    }

    public String[] includes() {
        return annotation.includes();
    }

    public String[] excludes() {
        return annotation.excludes();
    }

    @Override
    public void accept(BuilderMetadataVisitor visitor) {
        visitor.visitInputDirectory(this);
    }
}
