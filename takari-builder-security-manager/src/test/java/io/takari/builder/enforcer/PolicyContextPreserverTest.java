package io.takari.builder.enforcer;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.enforcer.ComposableSecurityManagerPolicy;
import io.takari.builder.enforcer.PolicyContextPreserver;

public class PolicyContextPreserverTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void setupSecurityManager() {
    ComposableSecurityManagerPolicy.setSystemSecurityManager();
  }

  @After
  public void teardownSecurityManager() {
    ComposableSecurityManagerPolicy.removeSystemSecurityManager();
  }


  @Test
  public void testNoSecurityManager() throws Exception {
    // assert policy preserver does not assume security manager is installed and configured
    PolicyContextPreserver preserver = new PolicyContextPreserver();
    AtomicInteger count = new AtomicInteger();
    preserver.wrap(() -> count.set(1)).run();
    assertEquals(1, count.get());
  }
}
