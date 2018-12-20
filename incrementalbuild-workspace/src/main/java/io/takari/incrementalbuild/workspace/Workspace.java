package io.takari.incrementalbuild.workspace;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

/**
 * {@code Workspace} provides a layer of indirection between BuildContext and underlying resource
 * (File) store.
 */
public interface Workspace {

  public enum Mode {
    NORMAL, DELTA, ESCALATED, SUPPRESSED;
  }

  public static enum ResourceStatus {
    NEW, MODIFIED, UNMODIFIED, REMOVED;
  }

  public static interface FileVisitor {
    public void visit(File file, long lastModified, long length, ResourceStatus status);
  }

  public Mode getMode();

  public Workspace escalate();

  public boolean isPresent(File file);

  public boolean isRegularFile(File file);

  public boolean isDirectory(File file);

  public void deleteFile(File file) throws IOException;

  public void processOutput(File file);

  public OutputStream newOutputStream(File file) throws IOException;

  public ResourceStatus getResourceStatus(File file, long lastModified, long length);

  /**
   * Walks a file tree.
   * <p>
   * Files visited and their status depends on workspace mode.
   * <ul>
   * <li><strong>{@code NORMAL}</strong> all files are visited and all file status is reported as
   * NEW. BuildContext is expected to calculate actual input resource status.</li>
   * <li><strong>{@code DELTA}</strong> only NEW, MODIFIED or REMOVED files are
   * visited.</strong></li>
   * <li><strong>{@code ESCALATED}</strong> all files are visited and all file status is reported as
   * NEW. This mode is used when the user has explicitly requested full build in IDE. BuildContext
   * must treat all files as either NEW or MODIFIED.</strong></li>
   * <li><strong>{@code SUPPRESSED}</strong> This mode is used during so-called "configuration"
   * build, when all inputs are assumed up-to-date, no outputs are expected to be created, updated
   * or removed. The idea is to allow host application to collect build configuration information
   * (compile source roots, properties, etc) without doing the actual build.</li>
   * </ul>
   */
  public void walk(File basedir, FileVisitor visitor) throws IOException;

  default public boolean isOptimizedBuildEnabled() {
    return false;
  }

  default public boolean hasProjectDependenciesChanged() {
    return true;
  }

  default public boolean hasDelta(Set<String> resources) {
    return true;
  }
}
