/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.IArtifactResources;
import java.net.URL;
import java.util.Collections;
import java.util.Set;

class ArtifactResourcesImpl implements IArtifactResources {

    public final IArtifactMetadata artifact;

    public final Set<URL> urls;

    public ArtifactResourcesImpl(IArtifactMetadata artifact, Set<URL> urls) {
        this.artifact = artifact;
        this.urls = Collections.unmodifiableSet(urls);
    }

    @Override
    public IArtifactMetadata artifact() {
        return artifact;
    }

    @Override
    public Set<URL> resources() {
        return urls;
    }
}
