package io.takari.incrementalbuild.spi;

import static io.takari.maven.testing.TestResources.touch;
import static org.junit.Assert.assertEquals;

import io.takari.builder.internal.workspace.IncrementalFileOutputStream;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.ResourceStatus;
import io.takari.incrementalbuild.workspace.Workspace;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
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
        public boolean isRegularFile(File file) {
            return Files.isRegularFile(file.toPath());
        }

        @Override
        public boolean isDirectory(File file) {
            return Files.isDirectory(file.toPath());
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
            // delta workspace returns resource status compared to the previous build
            if (added.contains(file)) {
                return ResourceStatus.NEW;
            }
            if (modified.contains(file)) {
                return ResourceStatus.MODIFIED;
            }
            if (removed.contains(file)) {
                return ResourceStatus.REMOVED;
            }
            return ResourceStatus.UNMODIFIED;
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
        TestBuildContext ctx;

        // initial build
        ctx = newBuildContext();
        File basedir = temp.newFolder("basedir");
        File a = temp.newFile("basedir/a").getCanonicalFile();
        assertEquals(
                1, toList(ctx.registerAndProcessInputs(basedir, null, null)).size());
        assertEquals(1, toList(ctx.getRegisteredInputs()).size());
        ctx.commit();

        // no change rebuild
        workspace = new DeltaWorkspace();
        ctx = newBuildContext(workspace);
        assertEquals(
                0, toList(ctx.registerAndProcessInputs(basedir, null, null)).size());
        assertEquals(1, toList(ctx.getRegisteredInputs()).size());
        ctx.commit();

        // add input
        workspace = new DeltaWorkspace();
        File b = temp.newFile("basedir/b").getCanonicalFile();
        workspace.added.add(b);
        ctx = newBuildContext(workspace);
        assertEquals(
                1, toList(ctx.registerAndProcessInputs(basedir, null, null)).size());
        assertEquals(2, toList(ctx.getRegisteredInputs()).size());
        ctx.commit();

        // modify input
        workspace = new DeltaWorkspace();
        workspace.modified.add(a);
        ctx = newBuildContext(workspace);
        assertEquals(
                1, toList(ctx.registerAndProcessInputs(basedir, null, null)).size());
        assertEquals(2, toList(ctx.getRegisteredInputs()).size());
        ctx.commit();

        // remove input
        workspace = new DeltaWorkspace();
        a.delete();
        workspace.removed.add(a);
        ctx = newBuildContext(workspace);
        assertEquals(
                0, toList(ctx.registerAndProcessInputs(basedir, null, null)).size());
        assertEquals(2, toList(ctx.getRegisteredInputs()).size());
        ctx.commit();
    }

    @Test
    public void testResourceStatus() throws Exception {
        File basedir = temp.newFolder("basedir");

        DeltaWorkspace workspace;
        TestBuildContext ctx;

        // initial build
        newBuildContext().commit();

        // new input
        workspace = new DeltaWorkspace();
        File a = temp.newFile("basedir/a").getCanonicalFile();
        workspace.added.add(a);
        ctx = newBuildContext(workspace);
        ResourceMetadata<File> input = only(ctx.registerInputs(basedir, null, null));
        assertEquals(ResourceStatus.NEW, input.getStatus());
        input.process();
        ctx.commit();

        // no-change rebuild
        workspace = new DeltaWorkspace();
        ctx = newBuildContext(workspace);
        input = only(ctx.registerInputs(basedir, null, null));
        assertEquals(ResourceStatus.UNMODIFIED, input.getStatus());
        ctx.commit();

        // modified input
        workspace = new DeltaWorkspace();
        touch(a);
        workspace.modified.add(a);
        ctx = newBuildContext(workspace);
        input = only(ctx.registerInputs(basedir, null, null));
        assertEquals(ResourceStatus.MODIFIED, input.getStatus());
        input.process();
        ctx.commit();

        // removed input
        workspace = new DeltaWorkspace();
        a.delete();
        workspace.removed.add(a);
        ctx = newBuildContext(workspace);
        assertEquals(0, toList(ctx.registerInputs(basedir, null, null)).size());
        assertEquals(ResourceStatus.REMOVED, ctx.getResourceStatus(a));
        input.process();
        ctx.commit();
    }

    @Test
    public void testCarryOverAndCleanup() throws Exception {
        File basedir = temp.newFolder("basedir").getCanonicalFile();
        File inputdir = temp.newFolder("basedir/inputdir").getCanonicalFile();
        File outputdir = temp.newFolder("basedir/outputdir").getCanonicalFile();
        File file = temp.newFile("basedir/inputdir/file.txt").getCanonicalFile();

        DeltaWorkspace workspace;
        TestBuildContext ctx;
        Collection<DefaultResource<File>> inputs;

        // initial build
        ctx = newBuildContext();
        inputs = ctx.registerAndProcessInputs(inputdir, null, null);
        Assert.assertEquals(1, inputs.size());
        inputs.iterator().next().associateOutput(temp.newFile("basedir/outputdir/file.out"));
        ctx.commit();

        // no-change rebuild
        workspace = new DeltaWorkspace();
        ctx = newBuildContext(workspace);
        ctx.registerAndProcessInputs(inputdir, null, null);
        ctx.commit();
        Assert.assertTrue(new File(outputdir, "file.out").exists());

        // "delete" input
        workspace = new DeltaWorkspace();
        workspace.removed.add(file);
        ctx = newBuildContext(workspace);
        ctx.registerAndProcessInputs(inputdir, null, null);
        ctx.commit();
        Assert.assertFalse(new File(outputdir, "file.out").exists());
    }

    private <T> T only(Iterable<T> values) {
        List<T> list = toList(values);
        assertEquals(1, list.size());
        return list.get(0);
    }
}
