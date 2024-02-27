/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal;

import io.takari.builder.ResourceType;
import java.io.Serializable;

@SuppressWarnings("serial")
public class CompileSourceRoot implements Serializable {

    // no serialVersionUID, want deserialization to fail if state format changes

    private final String path;
    private final ResourceType type;

    public CompileSourceRoot(String path, ResourceType type) {
        this.path = path;
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public ResourceType getType() {
        return type;
    }
}
