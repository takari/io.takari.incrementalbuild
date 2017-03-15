package io.takari.builder.testing;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import io.takari.builder.Builder;
import io.takari.builder.DependencyResources;
import io.takari.builder.ResolutionScope;
import io.takari.builder.enforcer.ComposableSecurityManagerPolicy;
import io.takari.builder.enforcer.Policy;
import io.takari.maven.testing.TestResources;

public class ReadFromJarTest {

  @Rule
  public final TestResources resources = new TestResources();

  public static class _Builder {

    @DependencyResources(includes = "LICENSE-junit.txt", scope = ResolutionScope.COMPILE)
    List<URL> resources;

    @Builder(name = "build")
    void build() {}

  }

  @Test
  public void testReadJarDoesNotTriggerWriteCheck() throws Exception {
    File basedir = resources.getBasedir("jar-reader");
    File junitJar = new File(basedir, "junit-4.12.jar");

    enterTestScope();
    try {
      BuilderExecution.builderExecution(basedir, _Builder.class)
          .withDependency("junit:junit:4.12", junitJar).executeWithoutRuntime();
    } finally {
      leaveTestScope();
    }
  }

  private static void enterTestScope() {
    ComposableSecurityManagerPolicy.setSystemSecurityManager();
    ComposableSecurityManagerPolicy.setDefaultPolicy(new Policy() {

      @Override
      public void checkWrite(String file) {
        // throw new SecurityException();
      }

      @Override
      public void checkSocketPermission() {}

      @Override
      public void checkRead(String file) {}

      @Override
      public void checkPropertyPermission(String action, String name) {}

      @Override
      public void checkExec(String cmd) {}
    });
  }

  private static void leaveTestScope() {
    ComposableSecurityManagerPolicy.setDefaultPolicy(null);
    ComposableSecurityManagerPolicy.removeSystemSecurityManager();
  }

}
