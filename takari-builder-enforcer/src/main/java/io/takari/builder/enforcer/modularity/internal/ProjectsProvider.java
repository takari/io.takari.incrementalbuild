/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.enforcer.modularity.internal;

import java.util.Collection;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

interface ProjectsProvider {

    public MavenProject getTopLevelProject(MavenSession session);

    public Collection<MavenProject> getAllProjects(MavenSession session);

    public Collection<MavenProject> getUpstreamProjects(MavenSession session, MavenProject project, boolean transitive);
}
