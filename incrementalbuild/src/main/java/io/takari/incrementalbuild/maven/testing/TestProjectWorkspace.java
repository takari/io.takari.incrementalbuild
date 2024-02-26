/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.maven.testing;

import io.takari.incrementalbuild.maven.internal.FilesystemWorkspace;
import io.takari.incrementalbuild.maven.internal.ProjectWorkspace;
import io.takari.incrementalbuild.workspace.Workspace;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import javax.inject.Inject;
import org.apache.maven.project.MavenProject;

// this is explicitly bound in IncrementalBuildRuntime.addGuiceModules
class TestProjectWorkspace extends ProjectWorkspace implements Workspace {

    private final IncrementalBuildLog log;

    private class ForwardingWorkspace implements Workspace {

        private final Workspace workspace;

        public ForwardingWorkspace(Workspace workspace) {
            this.workspace = workspace;
        }

        @Override
        public Mode getMode() {
            return workspace.getMode();
        }

        @Override
        public Workspace escalate() {
            throw new UnsupportedOperationException(); // already escalated
        }

        @Override
        public boolean isPresent(File file) {
            return workspace.isPresent(file);
        }

        @Override
        public boolean isRegularFile(File file) {
            return workspace.isRegularFile(file);
        }

        @Override
        public boolean isDirectory(File file) {
            return workspace.isDirectory(file);
        }

        @Override
        public void deleteFile(File file) throws IOException {
            log.addDeletedOutput(file);

            workspace.deleteFile(file);
        }

        @Override
        public void processOutput(File file) {
            log.addRegisterOutput(file);

            workspace.processOutput(file);
        }

        @Override
        public OutputStream newOutputStream(File file) throws IOException {
            return workspace.newOutputStream(file);
        }

        @Override
        public ResourceStatus getResourceStatus(File file, long lastModified, long length) {
            return workspace.getResourceStatus(file, lastModified, length);
        }

        @Override
        public void walk(File basedir, FileVisitor visitor) throws IOException {
            workspace.walk(basedir, visitor);
        }
    }

    @Inject
    public TestProjectWorkspace(
            MavenProject project, Workspace workspace, FilesystemWorkspace filesystem, IncrementalBuildLog log) {
        super(project, workspace, filesystem);
        this.log = log;
    }

    @Override
    public void processOutput(File file) {
        log.addRegisterOutput(file);

        super.processOutput(file);
    }

    @Override
    public void deleteFile(File file) throws IOException {
        log.addDeletedOutput(file);

        super.deleteFile(file);
    }

    @Override
    public Workspace escalate() {
        return new ForwardingWorkspace(super.escalate());
    }

    @Override
    protected Workspace getWorkspace(File file) {
        return new ForwardingWorkspace(super.getWorkspace(file));
    }
}
