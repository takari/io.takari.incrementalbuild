/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import io.takari.builder.Parameter;
import java.util.Collections;
import java.util.List;

public class CompositeParameter extends AbstractParameter {

    private final Parameter annotation;

    /**
     * This composite parameter members.
     *
     * Note that the model allows duplicate member parameters (introduced by implementation
     * inheritance or developer-provided custom parameter name).
     */
    public final List<AbstractParameter> members;

    CompositeParameter(MemberAdapter element, TypeAdapter type, List<AbstractParameter> members) {
        super(element, type);
        this.annotation = element.getAnnotation(Parameter.class);
        this.members = Collections.unmodifiableList(members);
    }

    @Override
    public void accept(BuilderMetadataVisitor visitor) {
        if (visitor.enterComposite(this)) {
            members.forEach(v -> v.accept(visitor));
            visitor.leaveComposite(this);
        }
    }

    @Override
    public Parameter annotation() {
        return annotation;
    }

    @Override
    public boolean required() {
        return annotation != null && annotation.required();
    }
}
