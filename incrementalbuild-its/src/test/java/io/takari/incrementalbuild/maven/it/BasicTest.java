package io.takari.incrementalbuild.maven.it;

import java.io.File;
import java.io.IOException;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class BasicTest {

  @Rule
  public final TestResources resources = new TestResources("src/test/it", "target/it");

  private final String mavenVersion = "3.2.0-SNAPSHOT";

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

  protected Verifier getVerifier(File basedir) throws VerificationException, IOException {
    File mavenHome = new File("target/dependency/apache-maven-" + mavenVersion);
    Assert.assertTrue("Can't locate maven home, make sure to run 'mvn generate-test-resources': "
        + mavenHome, mavenHome.isDirectory());
    // XXX somebody needs to fix this in maven-verifier already
    System.setProperty("maven.home", mavenHome.getAbsolutePath());
    return new Verifier(basedir.getAbsolutePath());
  }

}
