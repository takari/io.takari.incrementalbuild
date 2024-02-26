/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import io.takari.builder.GeneratedSourcesDirectory;

public class GeneratedSourcesDirectoryParameter extends AbstractFileParameter<GeneratedSourcesDirectory> {

    public GeneratedSourcesDirectoryParameter(MemberAdapter element, TypeAdapter type) {
        super(element, type, GeneratedSourcesDirectory.class);
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
    public boolean required() {
        return annotation.required();
    }

    @Override
    public void accept(BuilderMetadataVisitor visitor) {
        visitor.visitGeneratedSourcesDirectory(this);
    }
}
