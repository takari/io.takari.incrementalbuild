package io.takari.builder.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.takari.builder.internal.workspace.FilesystemWorkspace;
import io.takari.incrementalbuild.workspace.Workspace;
import io.takari.incrementalbuild.workspace.Workspace.FileVisitor;
import io.takari.incrementalbuild.workspace.Workspace.ResourceStatus;

public class BuilderWorkspace {

  private final Workspace workspace;
  private final FilesystemWorkspace filesystem = new FilesystemWorkspace();
  private final Path projectBasedir;
  private final BuilderExecutionState oldExecutionState;

  public BuilderWorkspace(Workspace workspace, Path basedir,
      BuilderExecutionState oldExecutionState) {
    this.workspace = workspace;
    this.projectBasedir = basedir.normalize();
    this.oldExecutionState = oldExecutionState;
  }

  public Stream<Path> walk(Path basedir) throws IOException {
    switch (getWorkspace(basedir).getMode()) {
      case SUPPRESSED:
        // workspace.walk will not return anything, just get from previous execution state
        return getUnchanged(basedir, null).stream();
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
        if (!getWorkspace(basedir).getResourceStatus(file, lastModified, length)
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
        .filter(p -> p.startsWith(basedir)).collect(Collectors.toCollection(LinkedHashSet::new));
  }

}
