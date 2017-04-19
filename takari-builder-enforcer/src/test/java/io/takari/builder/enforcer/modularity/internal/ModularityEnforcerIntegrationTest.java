package io.takari.builder.enforcer.modularity.internal;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.takari.maven.testing.TestResources;
import io.takari.maven.testing.executor.MavenRuntime;
import io.takari.maven.testing.executor.MavenRuntime.MavenRuntimeBuilder;
import io.takari.maven.testing.executor.MavenVersions;
import io.takari.maven.testing.executor.junit.MavenJUnitTestRunner;

@RunWith(MavenJUnitTestRunner.class)
@MavenVersions({"3.3.9"})
public class ModularityEnforcerIntegrationTest {

  @Rule
  public final TestResources resources = new TestResources();
  
  public final MavenRuntime maven;

  public ModularityEnforcerIntegrationTest(MavenRuntimeBuilder mavenBuilder) throws Exception {
    this.maven = mavenBuilder.withCliOptions("-B", "-U", "-e").build();
  }

  @Test
  public void testPackageMultimodule() throws Exception {
    File basedir = resources.getBasedir("multimodule-it");
    maven.forProject(basedir) //
        .withCliOption("-Dmodularity.enforcer.allow.breaking.exceptions=true") //
        .execute("package") //
        .assertErrorFreeLog()
        .assertLogText("Modularity Enforcer is enabled");
    TestResources.assertFileContents("Hello world.\n", basedir, "m1/target/file.txt");
  }

  @Test
  public void testCompileMultimodule() throws Exception {
    File basedir = resources.getBasedir("multimodule-it");
    maven.forProject(basedir) //
        .withCliOption("-Dit-phase=compile") //
        .withCliOption("-Dmodularity.enforcer.allow.breaking.exceptions=true") //
        .execute("compile") //
        .assertErrorFreeLog()
        .assertLogText("Modularity Enforcer is enabled");
    TestResources.assertFileContents("Hello world.\n", basedir, "m1/target/file.txt");
  }

  @Test
  public void testReadFromnSourcesApt() throws Exception {
    File basedir = resources.getBasedir("multimodule-apt-it");
    maven.forProject(basedir) //
        .withCliOption("-Dmodularity.enforcer.allow.breaking.exceptions=true") //
        .execute("compile") //
        .assertErrorFreeLog()
        .assertLogText("Modularity Enforcer is enabled");
  }

  @Test
  public void testEnforceLogOnlyWithCommandLineProp() throws Exception {
    File basedir = resources.getBasedir("enforce");
    maven.forProject(basedir) //
        .withCliOption("-Dmodularity.enforcer.logonly=true")
        .withCliOption("-Dmodularity.enforcer.allow.breaking.exceptions=true") //
        .execute("compile") //
        .assertLogText("Modularity Enforcer is disabled")
        .assertLogText("/file.txt")
        .assertLogText("[INFO] BUILD SUCCESS")  //very important for test
        .assertLogText("[ERROR] W")
        .assertLogText("Unexpected filesystem access for project: enforce")
        .assertLogText("Violated Rules Are:")
        .assertLogText("  W ")
        .assertLogText("/target/test-projects/ModularityEnforcerIntegrationTest_testEnforceLogOnlyWithCommandLineProp[3.3.9]_enforce/**");
  }

  @Test
  public void testEnforceDisabledWithCommandLineProp() throws Exception {
    File basedir = resources.getBasedir("enforce");
    maven.forProject(basedir) //
        .withCliOption("-Dmodularity.enforcer.disabled=true")
        .execute("compile") //
        .assertErrorFreeLog()
        .assertLogText("[WARNING] Modularity Enforcer is disabled: don't trust this build");
  }

  @Test
  public void testEnforceDisabledViaProfile() throws Exception {
    File basedir = resources.getBasedir("enforce");
    maven.forProject(basedir) //
        .withCliOption("-DenforcerDisabled=true")
        .execute("compile") //
        .assertErrorFreeLog()
        .assertLogText("[WARNING] Modularity Enforcer is disabled: don't trust this build");
  }
  
  @Test
  public void testEnforceDisabledInPom() throws Exception {
    File basedir = resources.getBasedir("enforce-disabled");
    maven.forProject(basedir) //
        .execute("compile") //
        .assertErrorFreeLog()
        .assertLogText("[WARNING] Modularity Enforcer is disabled: don't trust this build");
  }

  @Test
  public void testEnforce() throws Exception {
    File basedir = resources.getBasedir("enforce");
    maven.forProject(basedir) //
        .execute("compile") //
        .assertLogText("[ERROR] W") //
        .assertLogText("enforce/file.txt");
  }
}
