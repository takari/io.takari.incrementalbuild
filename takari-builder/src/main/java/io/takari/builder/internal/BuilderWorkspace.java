/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal;

import io.takari.builder.internal.workspace.FilesystemWorkspace;
import io.takari.incrementalbuild.workspace.Workspace;
import io.takari.incrementalbuild.workspace.Workspace.FileVisitor;
import io.takari.incrementalbuild.workspace.Workspace.Mode;
import io.takari.incrementalbuild.workspace.Workspace.ResourceStatus;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BuilderWorkspace {

    private final Workspace workspace;
    private final FilesystemWorkspace filesystem = new FilesystemWorkspace();
    private final Path projectBasedir;
    private final BuilderExecutionState oldExecutionState;

    public BuilderWorkspace(Workspace workspace, Path basedir, BuilderExecutionState oldExecutionState) {
        this.projectBasedir = basedir.normalize();
        this.oldExecutionState = oldExecutionState;
        if (workspace.getMode() == Mode.DELTA && oldExecutionState.isEscalated()) {
            // Given an escalated execution state, a build must be run for this builder
            // However, if the workspace is in a delta mode, it must be escalated since
            // delta mode would depend on the workspace state for inputs. Escalating the workspace
            // will force a build that the escalated execution state has noted
            this.workspace = workspace.escalate();
        } else {
            this.workspace = workspace;
        }
    }

    public Stream<Path> walk(Path basedir) throws IOException {
        switch (getMode(basedir)) {
            case SUPPRESSED:
                // workspace.walk will walk all resources to calculate inputs, but build will still be
                // skipped in BuilderRunner#execute()
            case DELTA:
                // workspace.walk will only return changed resources,
                // combine these with unchanged resources from previous execution state
                Set<Path> changed = doWorkspaceWalk(basedir);
                Set<Path> unchanged = getUnchanged(basedir, changed);

                changed.addAll(unchanged);

                return changed.stream();
            case NORMAL:
            case ESCALATED:
                // workspace.walk will return everything we need
                return doWorkspaceWalk(basedir).stream();
        }
        // this should not end up here
        return Stream.of();
    }

    public Mode getMode(Path basedir) {
        return getWorkspace(basedir).getMode();
    }

    public boolean isRegularFile(Path path) {
        return getWorkspace(path).isRegularFile(path.toFile());
    }

    public boolean exists(Path path) {
        return getWorkspace(path).isRegularFile(path.toFile())
                || getWorkspace(path).isDirectory(path.toFile());
    }

    public boolean isDirectory(Path path) {
        return getWorkspace(path).isDirectory(path.toFile());
    }

    public void processOutput(Path path) {
        getWorkspace(path).processOutput(path.toFile());
    }

    private Workspace getWorkspace(Path path) {
        if (path.normalize().startsWith(projectBasedir)) {
            return workspace;
        }
        return filesystem;
    }

    private Set<Path> doWorkspaceWalk(Path basedir) throws IOException {
        Set<Path> files = new LinkedHashSet<>();
        getWorkspace(basedir).walk(basedir.toFile(), new FileVisitor() {

            @Override
            public void visit(File file, long lastModified, long length, ResourceStatus status) {
                // only return files that exist
                if (!getWorkspace(basedir)
                        .getResourceStatus(file, lastModified, length)
                        .equals(ResourceStatus.REMOVED)) {
                    files.add(file.toPath());
                }
            }
        });
        return files;
    }

    private Set<Path> getUnchanged(Path basedir, Set<Path> changed) {
        if (oldExecutionState == null) {
            return Collections.emptySet();
        }
        return oldExecutionState.inputsDigest.files().stream()
                .filter(p -> changed == null || !changed.contains(p))
                .filter(p -> getWorkspace(p).isRegularFile(p.toFile())
                        || getWorkspace(p).isDirectory(p.toFile()))
                .filter(p -> p.startsWith(basedir))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
