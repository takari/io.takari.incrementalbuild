/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.workspace;

import io.takari.incrementalbuild.workspace.Workspace;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import javax.inject.Named;

@Named
public class FilesystemWorkspace implements Workspace {

    @Override
    public Mode getMode() {
        return Mode.NORMAL;
    }

    @Override
    public Workspace escalate() {
        return this;
    }

    @Override
    public void deleteFile(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete file " + file);
        }
    }

    @Override
    public void processOutput(File outputFile) {}

    @Override
    public OutputStream newOutputStream(File file) throws IOException {
        return new IncrementalFileOutputStream(file);
    }

    @Override
    public ResourceStatus getResourceStatus(File file, long lastModified, long length) {
        if (!isRegularFile(file) && !isDirectory(file)) {
            return ResourceStatus.REMOVED;
        }
        if (length == file.length() && lastModified == file.lastModified()) {
            return ResourceStatus.UNMODIFIED;
        }
        return ResourceStatus.MODIFIED;
    }

    @Override
    public boolean isPresent(File file) {
        return file != null && file.isFile() && file.canRead();
    }

    @Override
    public boolean isRegularFile(File file) {
        return Files.isRegularFile(file.toPath());
    }

    @Override
    public boolean isDirectory(File file) {
        return Files.isDirectory(file.toPath());
    }

    @Override
    public void walk(File basedir, final FileVisitor visitor) throws IOException {
        if (!basedir.exists()) {
            return;
        }
        final Path basepath = basedir.toPath();
        Files.walkFileTree(basepath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                // BasicFileAttributes#lastModifiedTime() and File#lastModified() appear to have different
                // resolution in some environments and mixing the two results in "Unexpected input change"
                // exceptions.
                // https://github.com/takari/io.takari.incrementalbuild/pull/5
                final File file = path.toFile();
                final long lastModified = file.lastModified();
                final long length = file.length();
                visitor.visit(file, lastModified, length, ResourceStatus.NEW);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
