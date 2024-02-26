package io.takari.builder.internal;

import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;

import io.takari.builder.OutputDirectory;
import io.takari.builder.internal.BuilderInputs.OutputDirectoryValue;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class OutputDirectoryParameterTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    static class _Data {
        @OutputDirectory(defaultValue = "target")
        File outputDirectory;
    }

    @Test
    public void testOutputDirectory() throws Exception {
        File basedir = temp.newFolder().getCanonicalFile();
        File target = new File(basedir, "target");

        OutputDirectoryValue outputDirectory = builder(basedir).build(_Data.class, "outputDirectory");

        assertEquals(target, outputDirectory.value());
        // TODO assertFiles(data.getOutputDirectories(), target);
    }

    //
    //
    //

    static class _PathData {
        @OutputDirectory(defaultValue = "target")
        Path outputDirectory;
    }

    @Test
    public void testPathOutputDirectory() throws Exception {
        File basedir = temp.newFolder().getCanonicalFile();
        Path target = Paths.get(basedir.getCanonicalPath(), "target");

        OutputDirectoryValue outputDirectory = builder(basedir).build(_PathData.class, "outputDirectory");

        assertEquals(target, outputDirectory.value());
        // TODO assertFiles(data.getOutputDirectories(), target);
    }
}
