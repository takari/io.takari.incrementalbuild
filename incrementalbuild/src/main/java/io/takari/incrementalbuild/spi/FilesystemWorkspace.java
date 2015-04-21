package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import io.takari.incrementalbuild.workspace.Workspace;

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
    if (!isPresent(file)) {
      return ResourceStatus.REMOVED;
    }
    if (length == file.length() && lastModified / 1000 == file.lastModified() / 1000) {
      return ResourceStatus.UNMODIFIED;
    }
    return ResourceStatus.MODIFIED;
  }

  @Override
  public boolean isPresent(File file) {
    return file != null && file.isFile() && file.canRead();
  }

  @Override
  public void walk(File basedir, final FileVisitor visitor) throws IOException {
    if (!basedir.isDirectory()) {
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
