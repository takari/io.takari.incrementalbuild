package io.takari.builder.internal;

import static org.junit.Assert.assertEquals;

import io.takari.incrementalbuild.workspace.Workspace;
import io.takari.incrementalbuild.workspace.Workspace.Mode;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import org.junit.Test;

public class BuilderWorkspaceTest {

    @Test
    public void testDeltaWorkspaceWithEscalatedExecutionState() {
        // BuilderWorkspace should escalate the workspace
        // If the workspace is in a DELTA mode and
        // If the old execution state is completely empty
        TestWorkspace workspace = new TestWorkspace(Mode.DELTA);
        BuilderExecutionState state = BuilderExecutionState.load(Paths.get(""));
        BuilderWorkspace builderWorkspace = new BuilderWorkspace(workspace, Paths.get(""), state);
        assertEquals(Mode.ESCALATED, builderWorkspace.getMode(Paths.get("")));
    }

    static class TestWorkspace implements Workspace {
        public Mode mode;

        public TestWorkspace(Mode mode) {
            this.mode = mode;
        }

        @Override
        public Mode getMode() {
            return mode;
        }

        @Override
        public Workspace escalate() {
            return new TestWorkspace(Mode.ESCALATED);
        }

        @Override
        public boolean isPresent(File file) {
            return false;
        }

        @Override
        public boolean isRegularFile(File file) {
            return false;
        }

        @Override
        public boolean isDirectory(File file) {
            return false;
        }

        @Override
        public void deleteFile(File file) throws IOException {}

        @Override
        public void processOutput(File file) {}

        @Override
        public OutputStream newOutputStream(File file) throws IOException {
            return null;
        }

        @Override
        public ResourceStatus getResourceStatus(File file, long lastModified, long length) {
            return null;
        }

        @Override
        public void walk(File basedir, FileVisitor visitor) throws IOException {}
    }
}
