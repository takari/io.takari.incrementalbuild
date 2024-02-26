/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import java.lang.annotation.Annotation;

public class MultivalueParameter extends AbstractParameter {

    public final AbstractParameter elements;

    /**
     * @param element is the originating element of the multivalue parameter
     * @param type is the type of the originating element
     * @param elements is the metadata of this array/collection elements
     */
    public MultivalueParameter(MemberAdapter element, TypeAdapter type, AbstractParameter elements) {
        super(element, type);
        this.elements = elements;
    }

    @Override
    public void accept(BuilderMetadataVisitor visitor) {
        if (visitor.enterMultivalue(this)) {
            elements.accept(visitor);
            visitor.leaveMultivalue(this);
        }
    }

    @Override
    public Annotation annotation() {
        return null;
    }

    @Override
    public boolean required() {
        // TODO Auto-generated method stub
        return false;
    }
}
