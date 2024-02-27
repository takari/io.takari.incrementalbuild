package io.takari.builder.demo;

import static io.takari.maven.testing.TestResources.assertDirectoryContents;

import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Maven plugin integration tests are optional and validate that builders works as designed during a
 * Maven build. Integration tests can run with multiple versions of Maven, as defined by
 * {@link MavenVersions} test annotation.
 */
@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class CopyFilesMavenIntegrationTest {

    @Rule
    public final TestResources resources = new TestResources();

    public final TestProperties properties = new TestProperties();

    private MavenRuntime verifier;

    public CopyFilesMavenIntegrationTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
        this.verifier = verifierBuilder //
                .withCliOptions("-U", "-B", "-e") //
                .build();
    }

    @Test
    public void testCopyFiles_basic() throws Exception {
        File basedir = resources.getBasedir("basic-it");

        verifier.forProject(basedir).execute("compile").assertErrorFreeLog();

        assertDirectoryContents(new File(basedir, "target/files"), "file.txt");
    }
}
