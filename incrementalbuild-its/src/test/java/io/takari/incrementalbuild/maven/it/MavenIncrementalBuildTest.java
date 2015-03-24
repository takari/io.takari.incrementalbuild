package io.takari.incrementalbuild.maven.it;

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

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.2.3", "3.2.5", "3.3.1"})
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
    verifier.forProject(new File(basedir, "project")).execute("compile").assertErrorFreeLog();
    TestResources.assertFilesPresent(basedir, "project/target/output.txt");

    // build extension should be optional
    verifier.forProject(new File(basedir, "project-noextension")).execute("compile")
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

}
