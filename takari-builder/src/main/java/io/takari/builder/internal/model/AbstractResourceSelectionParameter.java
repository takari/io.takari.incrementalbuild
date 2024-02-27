/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

public abstract class AbstractResourceSelectionParameter extends AbstractParameter {

    protected AbstractResourceSelectionParameter(MemberAdapter element, TypeAdapter type) {
        super(element, type);
    }

    public abstract String[] value();

    public abstract String[] defaultValue();

    public abstract String[] includes();

    public abstract String[] defaultIncludes();

    public abstract String[] excludes();

    public abstract String[] defaultExcludes();
}
