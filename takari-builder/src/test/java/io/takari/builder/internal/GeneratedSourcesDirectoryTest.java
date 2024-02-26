package io.takari.builder.internal;

import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;

import io.takari.builder.GeneratedSourcesDirectory;
import io.takari.builder.internal.BuilderInputs.GeneratedSourcesDirectoryValue;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GeneratedSourcesDirectoryTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    static class _Data {
        @GeneratedSourcesDirectory
        File dir;
    }

    @Test
    public void testAnnotation() throws Exception {
        File basedir = temp.newFolder().getCanonicalFile();

        GeneratedSourcesDirectoryValue value = builder(basedir) //
                .withConfigurationXml("<dir>generated</dir>") //
                .build(_Data.class, "dir");

        assertEquals(new File(basedir, "generated"), value.value());
    }

    @Test
    public void testCompileSourceRoots() throws Exception {
        File basedir = temp.newFolder().getCanonicalFile();

        BuilderInputs inputs =
                builder(basedir).withConfigurationXml("<dir>generated</dir>").build(_Data.class);

        List<CompileSourceRoot> roots = new ArrayList<>(inputs.getCompileSourceRoots());
        assertEquals(1, roots.size());
        assertEquals(
                new File(basedir, "generated").getCanonicalPath(), roots.get(0).getPath());
    }

    //
    //
    //

    static class _PathData {
        @GeneratedSourcesDirectory
        Path dir;
    }

    @Test
    public void testPathAnnotation() throws Exception {
        File basedir = temp.newFolder().getCanonicalFile();

        GeneratedSourcesDirectoryValue value = builder(basedir) //
                .withConfigurationXml("<dir>generated</dir>") //
                .build(_PathData.class, "dir");

        assertEquals(Paths.get(basedir.getCanonicalPath(), "generated"), value.value());
    }
}
