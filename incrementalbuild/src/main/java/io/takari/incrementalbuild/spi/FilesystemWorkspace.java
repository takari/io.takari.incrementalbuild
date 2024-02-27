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
import java.io.IOException;
import java.io.OutputStream;

public class FilesystemWorkspace implements Workspace {

    private final io.takari.builder.internal.workspace.FilesystemWorkspace delegate =
            new io.takari.builder.internal.workspace.FilesystemWorkspace();

    @Override
    public Mode getMode() {
        return delegate.getMode();
    }

    @Override
    public Workspace escalate() {
        return this;
    }

    @Override
    public boolean isPresent(File file) {
        return delegate.isPresent(file);
    }

    @Override
    public boolean isRegularFile(File file) {
        return delegate.isRegularFile(file);
    }

    @Override
    public boolean isDirectory(File file) {
        return delegate.isDirectory(file);
    }

    @Override
    public void deleteFile(File file) throws IOException {
        delegate.deleteFile(file);
    }

    @Override
    public void processOutput(File file) {
        delegate.processOutput(file);
    }

    @Override
    public OutputStream newOutputStream(File file) throws IOException {
        return delegate.newOutputStream(file);
    }

    @Override
    public ResourceStatus getResourceStatus(File file, long lastModified, long length) {
        return delegate.getResourceStatus(file, lastModified, length);
    }

    @Override
    public void walk(File basedir, FileVisitor visitor) throws IOException {
        delegate.walk(basedir, visitor);
    }
}
