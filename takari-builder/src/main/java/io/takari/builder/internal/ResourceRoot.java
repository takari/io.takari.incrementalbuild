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
import java.util.List;

@SuppressWarnings("serial")
public class ResourceRoot implements Serializable {

    // no serialVersionUID, want deserialization to fail if state format changes

    private final String location;
    private final ResourceType resourceType;
    private final List<String> includes;
    private final List<String> excludes;

    public ResourceRoot(String location, ResourceType resourceType, List<String> includes, List<String> excludes) {
        this.location = location;
        this.resourceType = resourceType;
        this.includes = includes;
        this.excludes = excludes;
    }

    public String getLocation() {
        return location.toString();
    }

    public ResourceType getResourceType() {
        return this.resourceType;
    }

    public List<String> getIncludes() {
        return this.includes;
    }

    public List<String> getExcludes() {
        return this.excludes;
    }
}
