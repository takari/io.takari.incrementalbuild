package io.takari.builder.internal.maven;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class LegacyMojoWhitelistMavenTest {

    @Rule
    public final TestResources resources = new TestResources();

    private final MavenRuntime verifier;

    public LegacyMojoWhitelistMavenTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
        this.verifier = verifierBuilder //
                .withCliOptions("-B", "-e") //
                .build();
    }

    @Test
    public void testImportedModuleIsNotSubjectToMojoWhitelist() throws Exception {
        File basedir = resources.getBasedir("whiltelist-module-import");

        verifier.forProject(basedir).execute("validate").assertErrorFreeLog();
    }

    @Test
    public void testRedundantWhitelist() throws Exception {
        File basedir = resources.getBasedir("redundant-whitelist");

        verifier.forProject(basedir)
                .execute("package") //
                .assertLogText("[ERROR]")
                .assertLogText("Redundant whitelist entry");
    }

    @Test
    public void testWhitelistedExecution() throws Exception {
        File basedir = resources.getBasedir("whitelist-multi-module");

        verifier.forProject(basedir)
                .execute("validate")
                .assertLogText("[INFO] whitelist-multi-module ............................. SUCCESS")
                .assertLogText("[INFO] whitelisted-executions ............................. SUCCESS")
                .assertLogText("[INFO] non-whitelisted-executions ......................... FAILURE")
                .assertLogText(
                        "Unsupported legacy mojo org.apache.maven.plugins:maven-resources-plugin:2.7:resources {execution: not-allowed} @ non-whitelisted-executions");
    }
}
