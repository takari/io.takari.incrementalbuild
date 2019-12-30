package io.takari.incrementalbuild.maven.it;

import java.io.File;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.6.3"})
public class MavenIncrementalBuildTest {

  @Rule
  public final TestResources resources = new TestResources();

  public final TestProperties properties = new TestProperties();

  public final MavenRuntime verifier;

  public MavenIncrementalBuildTest(MavenRuntimeBuilder verifierBuilder) throws Exception {
    this.verifier = verifierBuilder //
        .withCliOptions("-U", "-B") //
        .withCliOptions("-Dapache-maven.version=" + properties.get("apache-maven.version")) //
        .withCliOptions("-Dincrementalbuild.version=" + properties.get("incrementalbuild.version")) //
        .build();
  }

  @Test
  public void testBasic_incrementalBuild() throws Exception {
    // install test plugin
    verifier.forProject(resources.getBasedir("test-plugin")) //
        .execute("install") //
        .assertErrorFreeLog();

    // build test project
    File basedir = resources.getBasedir("basic");
    verifier.forProject(basedir) //
        .execute("compile") //
        .assertErrorFreeLog();
    TestResources.assertFilesPresent(basedir, "target/output.txt");
  }

  @Test
  public void testBuildExtension() throws Exception {
    final File basedir = resources.getBasedir("buildextension");

    // install test plugin
    verifier.forProject(new File(basedir, "plugin")).execute("install").assertErrorFreeLog();

    //
    verifier.forProject(new File(basedir, "project")) //
        .withCliOption("-X") //
        .execute("compile").assertErrorFreeLog();
    TestResources.assertFilesPresent(basedir, "project/target/output.txt");

    // build extension should be optional
    verifier.forProject(new File(basedir, "project-noextension")) //
        .withCliOption("-X") //
        .execute("compile") //
        .assertErrorFreeLog();
    TestResources.assertFilesPresent(basedir, "project-noextension/target/output.txt");
  }

  @Test
  public void testMessages() throws Exception {
    File basedir = resources.getBasedir("message");

    // install test plugin
    verifier.forProject(new File(basedir, "plugin")).execute("install").assertErrorFreeLog();

    verifier.forProject(new File(basedir, "project")) //
        .withCliOption("-X") //
        .execute("compile") //
        .assertLogText("error message") //
        .assertLogText("warning message");
  }

  @Test
  @Ignore("Known problem, needs fixing")
  public void testSkipMojoExecution() throws Exception {
    // this test covers the following scenario
    // build #1
    // * a mojo processes some build inputs and generates some outputs
    // build #2
    // * the mojo does not call BuildContext (because all inputs were removed, for example)
    // build context is expected to remove outputs produced during build #1 but it does not

    // here is what happens behind the scenes
    // mojo instance and all required components, including BuildContext, are created
    // - MojoExecutionScopedBuildContext->MavenBuildContextConfiguration->MavenBuildContextFinalizer
    // - cannot be instantiated because mojo execution scope is not setup yet
    // mojo execution scope is setup
    // mojo is executed, but does not call into BuildContext and therefore scoped build context is
    // not instantiated
    // orphaned outputs are not cleaned up

    // need to force MavenBuildContextConfiguration instantiation somehow


    // install test plugin
    verifier.forProject(resources.getBasedir("test-plugin")) //
        .execute("install") //
        .assertErrorFreeLog();

    // build test project
    File basedir = resources.getBasedir("basic");
    verifier.forProject(basedir) //
        .execute("compile") //
        .assertErrorFreeLog();
    TestResources.assertFilesPresent(basedir, "target/output.txt");

    // build test project with -Dcopyfile.skip=true
    verifier.forProject(basedir) //
        .withCliOption("-Dcopyfile.skip=true") //
        .execute("compile") //
        .assertErrorFreeLog();
    TestResources.assertFilesNotPresent(basedir, "target/output.txt");
  }
}
