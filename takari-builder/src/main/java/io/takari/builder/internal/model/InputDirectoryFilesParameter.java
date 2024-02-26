/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import io.takari.builder.InputDirectoryFiles;

public class InputDirectoryFilesParameter extends AbstractResourceSelectionParameter {

    private final InputDirectoryFiles annotation;

    InputDirectoryFilesParameter(MemberAdapter element, TypeAdapter type) {
        super(element, type);
        this.annotation = element.getAnnotation(InputDirectoryFiles.class);
    }

    @Override
    public InputDirectoryFiles annotation() {
        return annotation;
    }

    @Override
    public boolean required() {
        return annotation.filesRequired();
    }

    @Override
    public String[] value() {
        return annotation.value();
    }

    @Override
    public String[] defaultValue() {
        return annotation.defaultValue();
    }

    @Override
    public String[] includes() {
        return annotation.includes();
    }

    @Override
    public String[] defaultIncludes() {
        return annotation.defaultIncludes();
    }

    @Override
    public String[] excludes() {
        return annotation.excludes();
    }

    @Override
    public String[] defaultExcludes() {
        return annotation.defaultExcludes();
    }

    @Override
    public void accept(BuilderMetadataVisitor visitor) {
        visitor.visitInputDirectoryFiles(this);
    }
}
