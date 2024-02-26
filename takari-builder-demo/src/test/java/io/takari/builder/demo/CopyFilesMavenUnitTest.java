package io.takari.builder.demo;

import static io.takari.maven.testing.TestResources.assertDirectoryContents;

import io.takari.builder.testing.BuilderExecution;
import io.takari.builder.testing.BuilderRuntime;
import io.takari.maven.testing.TestMavenRuntime;
import io.takari.maven.testing.TestResources;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;

/**
 * Maven plugin unit tests are only useful when porting existing Maven plugins to takari-builder
 * API.
 *
 * @deprecated {@link BuilderExecution} is the preferred way to test builder implementations.
 */
public class CopyFilesMavenUnitTest {

    @Rule
    public final BuilderRuntime enforcer = new BuilderRuntime();

    @Rule
    public final TestResources resources = new TestResources();

    @Rule
    public final TestMavenRuntime maven = new TestMavenRuntime();

    @Test
    public void testCopyFiles() throws Exception {
        File basedir = resources.getBasedir();
        new File(basedir, "src/files").mkdirs();
        new File(basedir, "src/files/file.txt").createNewFile();

        maven.executeMojo(basedir, "copy-files");

        assertDirectoryContents(new File(basedir, "target/files"), "file.txt");
    }
}
