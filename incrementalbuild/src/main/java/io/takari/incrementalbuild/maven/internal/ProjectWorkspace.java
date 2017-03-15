package io.takari.incrementalbuild.maven.internal;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;

import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.project.MavenProject;

import io.takari.incrementalbuild.workspace.Workspace;

/**
 * Eclipse Workspace implementation is scoped to a project and does not "see" resources outside
 * project basedir. This implementation dispatches Workspace calls to either Eclipse implementation
 * or Filesystem workspace implementation, depending on whether requested resource is inside or
 * outside of project basedir.
 */
@Typed(ProjectWorkspace.class)
@MojoExecutionScoped
public class ProjectWorkspace implements Workspace {

  private final Workspace workspace;

  private final FilesystemWorkspace filesystem;

  private final MavenProject project;

  private final Path basedir;

  @Inject
  public ProjectWorkspace(MavenProject project, Workspace workspace,
      FilesystemWorkspace filesystem) {
    this.project = project;
    this.basedir = project.getBasedir().toPath().normalize();
    this.workspace = workspace;
    this.filesystem = filesystem;
  }

  protected Workspace getWorkspace(File file) {
    if (file.toPath().normalize().startsWith(basedir)) {
      return workspace;
    }
    return filesystem;
  }

  @Override
  public Mode getMode() {
    return workspace.getMode();
  }

  @Override
  public Workspace escalate() {
    return new ProjectWorkspace(project, workspace.escalate(), filesystem);
  }

  @Override
  public boolean isPresent(File file) {
    return getWorkspace(file).isPresent(file);
  }

  @Override
  public boolean isRegularFile(File file) {
    return getWorkspace(file).isRegularFile(file);
  }

  @Override
  public boolean isDirectory(File file) {
    return getWorkspace(file).isDirectory(file);
  }

  @Override
  public void deleteFile(File file) throws IOException {
    getWorkspace(file).deleteFile(file);
  }

  @Override
  public void processOutput(File file) {
    getWorkspace(file).processOutput(file);
  }

  @Override
  public OutputStream newOutputStream(File file) throws IOException {
    return getWorkspace(file).newOutputStream(file);
  }

  @Override
  public ResourceStatus getResourceStatus(File file, long lastModified, long length) {
    return getWorkspace(file).getResourceStatus(file, lastModified, length);
  }

  @Override
  public void walk(File basedir, FileVisitor visitor) throws IOException {
    getWorkspace(basedir).walk(basedir, visitor);
  }
}
