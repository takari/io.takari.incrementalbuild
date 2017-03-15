package io.takari.builder.enforcer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.enforcer.modularity.internal.BasedirViolationException;
import io.takari.builder.enforcer.modularity.internal.ModularityEnforcerTestUtil;
import io.takari.builder.enforcer.modularity.internal.ModularityEnforcerTestUtil.Builder;
import io.takari.maven.testing.TestMavenRuntime;


public class PolicyContextPreserverTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final TestMavenRuntime maven = new TestMavenRuntime();

  private ModularityEnforcerTestUtil util;

  private Builder utilBuilder() {
    return new ModularityEnforcerTestUtil.Builder(maven);
  }

  @Before
  public void setupSecurityManager() {
    ComposableSecurityManagerPolicy.setSystemSecurityManager();
  }

  @After
  public void teardownSecurityManager() {
    ComposableSecurityManagerPolicy.removeSystemSecurityManager();
    util = null;
  }


  @Test
  public void testNoSecurityManager() throws Exception {
    // assert policy preserver does not assume security manager is installed and configured
    PolicyContextPreserver preserver = new PolicyContextPreserver();
    AtomicInteger count = new AtomicInteger();
    preserver.wrap(() -> count.set(1)).run();
    assertEquals(1, count.get());
  }

  @Test
  public void testStaleContext_runnable() throws Exception {
    util = utilBuilder().withReactorRoot(temp.newFolder()).build();
    util.setupContextForRootProject();

    PolicyContextPreserver preserver = new PolicyContextPreserver();

    SecurityManager securityManager = System.getSecurityManager();

    // this record policy violation in the context
    preserver.wrap(() -> securityManager.checkRead("/something/that/dont/exist1")).run();
    preserver.wrap(() -> {
      securityManager.checkRead("/something/that/dont/exist1");
      return null;
    }).call();


    assertNotNull(ComposableSecurityManagerPolicy.getContextPolicies());

    try {
      // this closes the context and throws BasedirViolationException
      util.finishContextForRootProject();
      fail();
    } catch (BasedirViolationException expected) {
      // the policy does not allow requested path read
    }

    try {
      // this attempts to access now out-of-scope policy
      preserver.wrap(() -> securityManager.checkRead("/something/that/dont/exist2")).run();
      fail();
    } catch (IllegalStateException expected) {
      // the context is closed
    }

    try {
      // this attempts to access now out-of-scope policy
      preserver.wrap(() -> {
        securityManager.checkRead("/something/that/dont/exist3");
        return null;
      }).call();
      fail();
    } catch (IllegalStateException expected) {
      // the context is closed
    }
  }

}
