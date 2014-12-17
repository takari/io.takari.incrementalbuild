package io.takari.incrementalbuild.spi;

import static org.junit.Assert.assertEquals;
import io.takari.incrementalbuild.workspace.Workspace;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class DeltaWorkspaceTest extends AbstractBuildContextTest {

  private static class DeltaWorkspace implements Workspace {

    public final Set<File> added = new HashSet<>();
    public final Set<File> modified = new HashSet<>();
    public final Set<File> removed = new HashSet<>();

    @Override
    public Mode getMode() {
      return Mode.DELTA;
    }

    @Override
    public Workspace escalate() {
      return new FilesystemWorkspace() {
        @Override
        public Mode getMode() {
          return Mode.ESCALATED;
        }
      };
    }

    @Override
    public boolean isPresent(File file) {
      return file.exists();
    }

    @Override
    public void deleteFile(File file) throws IOException {
      if (file.exists() && !file.delete()) {
        throw new IOException();
      }
    }

    @Override
    public void processOutput(File file) {}

    @Override
    public OutputStream newOutputStream(File file) throws IOException {
      return new IncrementalFileOutputStream(file);
    }

    @Override
    public ResourceStatus getResourceStatus(File file, long lastModified, long length) {
      if (file == null || !file.isFile() || !file.canRead()) {
        return ResourceStatus.REMOVED;
      }
      if (length == file.length() && lastModified == file.lastModified()) {
        return ResourceStatus.UNMODIFIED;
      }
      return ResourceStatus.MODIFIED;
    }

    @Override
    public void walk(File basedir, FileVisitor visitor) throws IOException {
      for (File file : added) {
        visitor.visit(file, file.lastModified(), file.length(), ResourceStatus.NEW);
      }
      for (File file : modified) {
        visitor.visit(file, file.lastModified(), file.length(), ResourceStatus.MODIFIED);
      }
      for (File file : removed) {
        visitor.visit(file, -1, 0, ResourceStatus.REMOVED);
      }
    }
  }

  @Test
  public void testGetRegisteredInputs() throws Exception {
    DeltaWorkspace workspace;
    DefaultBuildContext<?> ctx;

    // initial build
    ctx = newBuildContext();
    File basedir = temp.newFolder("basedir");
    File a = temp.newFile("basedir/a").getCanonicalFile();
    assertEquals(1, toList(ctx.registerAndProcessInputs(basedir, null, null)).size());
    assertEquals(1, toList(ctx.getRegisteredInputs()).size());
    ctx.commit();

    // no change rebuild
    workspace = new DeltaWorkspace();
    ctx = newBuildContext(workspace);
    assertEquals(0, toList(ctx.registerAndProcessInputs(basedir, null, null)).size());
    assertEquals(1, toList(ctx.getRegisteredInputs()).size());
    ctx.commit();

    // add input
    workspace = new DeltaWorkspace();
    File b = temp.newFile("basedir/b").getCanonicalFile();
    workspace.added.add(b);
    ctx = newBuildContext(workspace);
    assertEquals(1, toList(ctx.registerAndProcessInputs(basedir, null, null)).size());
    assertEquals(2, toList(ctx.getRegisteredInputs()).size());
    ctx.commit();

    // modify input
    workspace = new DeltaWorkspace();
    workspace.modified.add(a);
    ctx = newBuildContext(workspace);
    assertEquals(1, toList(ctx.registerAndProcessInputs(basedir, null, null)).size());
    assertEquals(2, toList(ctx.getRegisteredInputs()).size());
    ctx.commit();

    // remove input
    workspace = new DeltaWorkspace();
    a.delete();
    workspace.removed.add(a);
    ctx = newBuildContext(workspace);
    assertEquals(0, toList(ctx.registerAndProcessInputs(basedir, null, null)).size());
    assertEquals(2, toList(ctx.getRegisteredInputs()).size());
    ctx.commit();
  }
}
