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

public class UnsupportedCollectionParameter extends AbstractParameter {

    List<TypeAdapter> elementTypes;

    protected UnsupportedCollectionParameter(MemberAdapter element, TypeAdapter type, List<TypeAdapter> elementTypes) {
        super(element, type);
        this.elementTypes = elementTypes;
    }

    @Override
    public Annotation annotation() {
        return null;
    }

    @Override
    public boolean required() {
        return false;
    }

    @Override
    public void accept(BuilderMetadataVisitor visitor) {
        visitor.visitUnsupportedCollection(this);
    }
}
