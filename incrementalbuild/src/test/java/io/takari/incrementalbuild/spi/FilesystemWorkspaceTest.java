package io.takari.incrementalbuild.spi;

import static org.junit.Assert.assertEquals;
import io.takari.incrementalbuild.workspace.Workspace;
import io.takari.incrementalbuild.workspace.Workspace.ResourceStatus;

import java.io.File;

import org.junit.Test;

public class FilesystemWorkspaceTest extends AbstractBuildContextTest {
  @Test
  public void testUnmodifiedResourceStatus() throws Exception {
    final Workspace ws = new FilesystemWorkspace();
    temp.newFile("test");
    ws.walk(temp.getRoot(), new Workspace.FileVisitor() {
      @Override
      public void visit(File file, long lastModified, long length, ResourceStatus status) {
        ResourceStatus currentStatus = ws.getResourceStatus(file, lastModified, length);
        assertEquals(ResourceStatus.UNMODIFIED, currentStatus);
      }
    });
  }
}
