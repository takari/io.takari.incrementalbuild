package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import io.takari.incrementalbuild.workspace.Workspace;

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
