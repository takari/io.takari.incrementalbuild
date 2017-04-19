package io.takari.builder.enforcer.modularity.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.assertj.core.util.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.enforcer.ComposableSecurityManagerPolicy;
import io.takari.builder.enforcer.modularity.ProjectContext;
import io.takari.builder.enforcer.modularity.internal.ModularityEnforcerTestUtil.Builder;
import io.takari.maven.testing.TestMavenRuntime;

public class DefaultProjectBasedirEnforcerTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final TestMavenRuntime maven = new TestMavenRuntime();
  
  private ModularityEnforcerTestUtil util;

  @Before
  public void setupSecurityManager() {
    ComposableSecurityManagerPolicy.setSystemSecurityManager();
  }

  @After
  public void teardownSecurityManager() {
    ComposableSecurityManagerPolicy.removeSystemSecurityManager();
    util = null;
  }
  
  private Builder utilBuilder() {
    return new ModularityEnforcerTestUtil.Builder(maven);
  }

  @Test
  public void testRootMatch() throws Exception {

    File basedir = temp.newFolder();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        .withAllowReadByDefaultProperty(true)
        .withAllowWriteByDefaultProperty(true)
        .build();
    
    util.setupContextForRootProject();

    ProjectContext context = util.getProjectContext();
    
    assertTrue(context.checkRead("/foo/blah"));
    assertTrue(context.checkWrite("/foo/blah"));
    assertFalse(context.checkExecute("/foo/blah"));
  }
  
  @Test
  public void testBasicWriteToProjectNonTarget() throws Exception {
    File basedir = temp.newFolder();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        .build();
    
    util.setupContextForRootProject();
    
    assertFalse(util.getProjectContext().checkWrite(
        new File(basedir, "text.txt").getAbsolutePath()));
  }
  
  @Test
  public void testEnforceTempAccess() throws Exception {
    File basedir = temp.newFolder();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        .build();
    
    util.setupContextForRootProject();
    
    assertTrue(util.getProjectContext().checkRead(
        Files.newTemporaryFile().getAbsolutePath()));
  }
  
  @Test
  public void testEnforceWithBadException() throws Exception {
    File basedir = temp.newFolder();
    
    util = utilBuilder()
        .withReadException("*", "/stuff/")
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        .build();
    
    util.setupContextForRootProject();
    
    assertFalse(util.getProjectContext().checkWrite(
        new File(basedir, "stuff/text.txt").getAbsolutePath())); 
  }
  
  @Test
  public void testEnforceOnCommand() throws Exception {

    File basedir = temp.newFolder();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        .build();
    
    util.setupContextForRootProject();

    ProjectContext context = util.getProjectContext();
    
    assertFalse(context.checkExecute("git"));
  }
  
  @Test
  public void testEnforceOnCommandWithException() throws Exception {
    File basedir = temp.newFolder();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withExecException("*", "git")
        .withAllowBreakingExceptionsProperty(true)
        .build();
    
    util.setupContextForRootProject();

    ProjectContext context = util.getProjectContext();
    
    assertTrue(context.checkExecute("git"));
  }
  
  @Test
  public void testEnforceReadRootFileSystem() throws Exception {
    File basedir = temp.newFolder();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        .build();
    
    util.setupContextForRootProject();

    ProjectContext context = util.getProjectContext();
    
    assertFalse(context.checkRead("/some/file.txt"));
  }
  

  
  //
  // multimodule tests
  //
  
  @Test
  public void testDependencyALLAllowsParentsAsWell() throws Exception {
    File basedir = temp.newFolder("dependency-all");
    File module = new File(basedir, "m2");
    File file = new File(basedir, "text.txt");
    
    module.mkdirs();
    file.createNewFile();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withReadException("*", "${dependency.?ALL}/")
        .withDependentProject(basedir, module)
        .withAllowBreakingExceptionsProperty(true)
        .build();
    
    util.setupContextForProject(module);
    
    assertTrue(util.getProjectContext().checkRead(file.getAbsolutePath()));
  }
  
  @Test
  public void testEnforceReadFromNonDependencyReactor() throws Exception {
    File basedir = temp.newFolder();
    File module1 = new File(basedir, "m1");
    File module2 = new File(basedir, "m2");
    File file = new File(module1, "text.txt");
    
    module1.mkdirs();
    module2.mkdirs();
    file.createNewFile();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        // this rule should be ignored since this is a breaking rule
        .withReadException("m2", "/m1/text.txt")
        .withDependentProject(basedir, module1)
        .withDependentProject(basedir, module2, "m2")
        .build();
    
    util.setupContextForProject(module2);
    
    ProjectContext context = util.getProjectContext();
    
    assertFalse(context.checkRead(file.getAbsolutePath()));
  }
  
  @Test
  public void testEnforceReadFromNonDependencyReactorWithBadException() throws Exception {
    File basedir = temp.newFolder();
    File module1 = new File(basedir, "m1");
    File module2 = new File(basedir, "m2");
    File file = new File(module1, "text.txt");
    
    module1.mkdirs();
    module2.mkdirs();
    file.createNewFile();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        // this will fail because m1 is not a dependency of m2
        .withReadException("m2", "${dependency.m1}/text.txt")
        .withDependentProject(basedir, module1)
        .withDependentProject(basedir, module2, "m2")
        .build();
    
    util.setupContextForProject(module2);
    
    ProjectContext context = util.getProjectContext();
    
    assertFalse(context.checkRead(file.getAbsolutePath()));
  }
  
  @Test
  public void testEnforceReadFromDependencyReactor() throws Exception {
    File basedir = temp.newFolder();
    File module1 = new File(basedir, "m1");
    File module2 = new File(basedir, "m2");
    File file = new File(module1, "m1.txt");
    
    module1.mkdirs();
    module2.mkdirs();
    file.createNewFile();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        .withReadException("m2", "${dependency.m1}/${dependency.artifactId}.txt")
        .withDependentProject(basedir, module1, "m1")
        .withDependentProject(module1, module2, "m2")
        .build();
    
    util.setupContextForProject(module2);
    
    ProjectContext context = util.getProjectContext();
    
    assertTrue(context.checkRead(file.getAbsolutePath()));
  }
  
  @Test
  public void testEnforceReadFromAllDependencyReactor() throws Exception {
    File basedir = temp.newFolder();
    File module1 = new File(basedir, "m1");
    File module2 = new File(basedir, "m2");
    File file = new File(module1, "m1.txt");
    
    module1.mkdirs();
    module2.mkdirs();
    file.createNewFile();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        .withReadException("m2", "${dependency.?ALL}/")
        .withDependentProject(basedir, module1, "m1")
        .withDependentProject(module1, module2, "m2")
        .build();
    
    util.setupContextForProject(module2);
    
    ProjectContext context = util.getProjectContext();
    
    assertTrue(context.checkRead(file.getAbsolutePath()));
  }
  
  @Test
  public void testEnforceReadFromNonDependencyReactorPointingAtRoot() throws Exception {
    File basedir = temp.newFolder();
    File module1 = new File(basedir, "m1");
    File module2 = new File(basedir, "m2");
    File file = new File(module1, "m1.txt");
    
    module1.mkdirs();
    module2.mkdirs();
    file.createNewFile();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        .withReadException("m2", "/")
        .withDependentProject(basedir, module1, "m1")
        .withDependentProject(basedir, module2, "m2")
        .build();
    
    util.setupContextForProject(module2);
    
    ProjectContext context = util.getProjectContext();
    
    assertFalse(context.checkRead(file.getAbsolutePath()));
  }
  
  @Test
  public void testEnforceWithProjectExecption() throws Exception {
    File basedir = temp.newFolder();
    File module1 = new File(basedir, "m1");
    File module2 = new File(basedir, "m2");
    
    module1.mkdirs();
    module2.mkdirs();
    
    util = utilBuilder()
        .withReactorRoot(basedir)
        .withAllowBreakingExceptionsProperty(true)
        .withProjectExclusion("m2")
        .withDependentProject(basedir, module1, "m1")
        .withDependentProject(basedir, module2, "m2")
        .build();
    
    util.setupContextForProject(module2);
 
    assertFalse(util.hasProjectContext());
  }

}
