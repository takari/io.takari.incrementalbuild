/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.maven.internal;

import io.takari.incrementalbuild.maven.internal.digest.MojoConfigurationDigester;
import io.takari.incrementalbuild.spi.BuildContextEnvironment;
import io.takari.incrementalbuild.spi.BuildContextFinalizer;
import io.takari.incrementalbuild.workspace.Workspace;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.execution.scope.MojoExecutionScoped;

// TODO merge with MavenIncrementalConventions, not sure we need both

@Named
@MojoExecutionScoped
public class MavenBuildContextConfiguration implements BuildContextEnvironment {

    private final ProjectWorkspace workspace;
    private final File stateFile;
    private final Map<String, Serializable> parameters;
    private final MavenBuildContextFinalizer finalizer;

    @Inject
    public MavenBuildContextConfiguration(
            ProjectWorkspace workspace,
            MavenIncrementalConventions conventions,
            MojoConfigurationDigester digester,
            MavenBuildContextFinalizer finalizer)
            throws IOException {
        this.workspace = workspace;
        this.finalizer = finalizer;
        this.stateFile = conventions.getExecutionStateLocation();
        this.parameters = digester.digest();
    }

    @Override
    public File getStateFile() {
        return stateFile;
    }

    @Override
    public Workspace getWorkspace() {
        return workspace;
    }

    @Override
    public Map<String, Serializable> getParameters() {
        return parameters;
    }

    @Override
    public BuildContextFinalizer getFinalizer() {
        return finalizer;
    }
}
