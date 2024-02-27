/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.maven.internal;

import java.io.File;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;

@Named
@MojoExecutionScoped
public class MavenIncrementalConventions {

    private final MavenProject project;
    private final MojoExecution execution;

    @Inject
    public MavenIncrementalConventions(MavenProject project, MojoExecution execution) {
        this.project = project;
        this.execution = execution;
    }

    public File getExecutionStateLocation() {
        return getExecutionStateLocation(project, execution);
    }

    /**
     * Returns conventional location of MojoExecution incremental build state
     */
    public File getExecutionStateLocation(MavenProject project, MojoExecution execution) {
        File stateDirectory = getProjectStateLocation(project);
        String builderId = getExecutionId(execution);
        return new File(stateDirectory, builderId);
    }

    /**
     * Returns conventional MojoExecution identifier used by incremental build tools.
     */
    public String getExecutionId(MojoExecution execution) {
        PluginDescriptor pluginDescriptor = execution.getMojoDescriptor().getPluginDescriptor();
        StringBuilder builderId = new StringBuilder();
        builderId.append(pluginDescriptor.getGroupId()).append('_').append(pluginDescriptor.getArtifactId());
        builderId.append('_').append(execution.getGoal()).append('_').append(execution.getExecutionId());
        return builderId.toString();
    }

    /**
     * Returns conventional location of MavenProject incremental build state
     */
    public File getProjectStateLocation(MavenProject project) {
        return new File(project.getBuild().getDirectory(), "incremental");
    }
}
