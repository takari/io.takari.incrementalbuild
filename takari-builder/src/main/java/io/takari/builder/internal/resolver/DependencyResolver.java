/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.resolver;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.ResolutionScope;
import java.nio.file.Path;
import java.util.Map;

/**
 * Resolves @{@link DependencyResource} parameter values.
 */
public interface DependencyResolver {
    Map<IArtifactMetadata, Path> getProjectDependencies(boolean transitive, ResolutionScope scope);

    Map.Entry<IArtifactMetadata, Path> getProjectDependency(
            String groupId, String artifactId, String classifier, ResolutionScope scope);
}
