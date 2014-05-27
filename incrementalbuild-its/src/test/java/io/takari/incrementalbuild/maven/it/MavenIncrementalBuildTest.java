package io.takari.incrementalbuild.maven.it;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.codehaus.plexus.util.IOUtil;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class MavenIncrementalBuildTest {

  @Rule
  public final TestResources resources = new TestResources("src/test/it", "target/it");

  @Test
  public void testBasic_incrementalBuild() throws Exception {
    // install test project
    Verifier verifier = getVerifier(resources.getBasedir("test-plugin"));
    verifier.executeGoal("install");
    verifier.verifyErrorFreeLog();

    // build test project
    verifier = getVerifier(resources.getBasedir("basic"));
    verifier.executeGoal("compile");
    verifier.verifyErrorFreeLog();
    verifier.assertFilePresent("target/output.txt");
  }

  @Test
  public void testBuildExtension() throws Exception {
    Verifier verifier = getVerifier(resources.getBasedir("buildextension/plugin"));
    verifier.executeGoal("install");
    verifier.verifyErrorFreeLog();

    verifier = getVerifier(resources.getBasedir("buildextension/project"));
    verifier.executeGoal("compile");
    verifier.verifyErrorFreeLog();
    verifier.assertFilePresent("target/output.txt");

    // build extension should be optional
    verifier = getVerifier(resources.getBasedir("buildextension/project-noextension"));
    verifier.executeGoal("compile");
    verifier.verifyErrorFreeLog();
    verifier.assertFilePresent("target/output.txt");
  }

  @Test
  public void testMessages() throws Exception {
    Verifier verifier = getVerifier(resources.getBasedir("message/plugin"));
    verifier.executeGoal("install");
    verifier.verifyErrorFreeLog();

    verifier = getVerifier(resources.getBasedir("message/project"));
    verifier.setMavenDebug(true);
    try {
      verifier.executeGoal("compile");
      Assert.fail();
    } catch (VerificationException e) {
      // expected
    }
    verifier.verifyTextInLog("error message");
    verifier.verifyTextInLog("warning message");
  }

  protected Verifier getVerifier(File basedir) throws VerificationException, IOException {
    String mavenVersion = getTestProperty("apache-maven.version");
    File mavenHome = new File("target/dependency/apache-maven-" + mavenVersion);
    Assert.assertTrue("Can't locate maven home, make sure to run 'mvn generate-test-resources': "
        + mavenHome, mavenHome.isDirectory());
    // XXX somebody needs to fix this in maven-verifier already
    System.setProperty("maven.home", mavenHome.getAbsolutePath());
    Verifier verifier = new Verifier(basedir.getAbsolutePath());
    verifier.getCliOptions().add("-Dapache-maven.version=" + mavenVersion);
    verifier.getCliOptions().add(
        "-Dincrementalbuild.version=" + getTestProperty("incrementalbuild.version"));
    return verifier;
  }



  private static String getTestProperty(String name) throws IOException {
    Properties properties = new Properties();
    InputStream is =
        MavenIncrementalBuildTest.class.getClassLoader().getResourceAsStream("test.properties");
    try {
      properties.load(is);
    } finally {
      IOUtil.close(is);
    }
    String value = properties.getProperty(name);
    if (value == null) {
      throw new IllegalStateException("Undefined test property " + name);
    }
    return value;
  }
}
