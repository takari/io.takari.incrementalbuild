/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.workspace.Workspace;
import java.io.File;
import java.io.Serializable;
import java.util.Map;

public interface BuildContextEnvironment {

    public File getStateFile();

    // I don't like this here, but otherwise all implementations will have to explicitly depend on
    // Workspace, which I believe is worse.
    public Workspace getWorkspace();

    public Map<String, Serializable> getParameters();

    /**
     * Optional context finalizer.
     */
    public BuildContextFinalizer getFinalizer();
}
