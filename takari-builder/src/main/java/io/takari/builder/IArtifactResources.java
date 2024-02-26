/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder;

import java.net.URL;
import java.util.Set;

/**
 * Encapsulates resources selected from an artifact
 */
public interface IArtifactResources {

    IArtifactMetadata artifact();

    /**
     * Selected resources.
     *
     * <p>
     * URL#openStream returns InputStream of the resource contents. URL#getPath returns resource path
     * relative to artifact "base" (either zip file root or artifact directory basedir). Behaviour of
     * all other URL methods is undefined.
     */
    Set<URL> resources();
}
