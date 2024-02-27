/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.maven;

import io.takari.builder.IArtifactMetadata;
import org.apache.maven.artifact.Artifact;

class MavenArtifactMetadata implements IArtifactMetadata {

    private final Artifact artifact;

    MavenArtifactMetadata(Artifact artifact) {
        this.artifact = artifact;
    }

    @Override
    public String getGroupId() {
        return artifact.getGroupId();
    }

    @Override
    public String getArtifactId() {
        return artifact.getArtifactId();
    }

    @Override
    public String getVersion() {
        return artifact.getVersion();
    }

    @Override
    public String getType() {
        return artifact.getType();
    }

    @Override
    public String getClassifier() {
        return artifact.getClassifier();
    }
}
