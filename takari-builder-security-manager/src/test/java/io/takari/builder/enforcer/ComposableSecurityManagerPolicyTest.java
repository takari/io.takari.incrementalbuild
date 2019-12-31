package io.takari.builder.enforcer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.List;
import java.util.PropertyPermission;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.junit.Test;

public class ComposableSecurityManagerPolicyTest {

  private static ComposableSecurityManagerPolicy newPolicy(Policy... policies) {
    ComposableSecurityManagerPolicy policy = new ComposableSecurityManagerPolicy(null, null);
    Arrays.asList(policies).forEach(p -> policy.registerPolicy(new Object(), p));
    return policy;
  }

  @Test(expected = SecurityException.class)
  public void testSystemSetSecurityManager() throws Exception {
    // it should not be possible to replace *our* security manager at runtime
    try {
      ComposableSecurityManagerPolicy.setSystemSecurityManager();
      System.setSecurityManager(new SecurityManager());
    } finally {
      ComposableSecurityManagerPolicy.removeSystemSecurityManager();
    }
  }

  private Callable<Exception> getWork(String property) {
    return () -> {
      Exception out = null;
      try {
        // callable.call();
        System.getProperty(property);
      } catch (Exception e) {
        out = e;
      }
      return out;
    };
  }

  @Test
  public void testExecutorServiceThreadingWithSecurityManager() throws Exception {
    try {
      ComposableSecurityManagerPolicy.setSystemSecurityManager();
      ComposableSecurityManagerPolicy.setDefaultPolicy(new EmptyPolicy() {
        @Override
        public void checkPropertyPermission(String action, String name) {
          if ("not.allowed".equals(name)) {
            throw new RuntimeException("nope");
          }
        }
      });

      ExecutorService ex = Executors.newFixedThreadPool(2, Executors.defaultThreadFactory());

      List<String> exs1 = ex
          .invokeAll(
              Arrays.asList(getWork("not.allowed"), getWork("allowed"), getWork("not.allowed"),
                  getWork("allowed"), getWork("not.allowed"), getWork("allowed")))
          .stream().map(x -> {
            try {
              return x.get();
            } catch (InterruptedException | ExecutionException e1) {
              return e1;
            }
          }).map(e -> e != null ? e.getMessage() : null).collect(Collectors.toList());

      assertThat(exs1).containsExactly("nope", null, "nope", null, "nope", null);
    } finally {
      ComposableSecurityManagerPolicy.removeSystemSecurityManager();
    }
  }

  @Test
  public void testParallelStreamsThreadingWithSecurityManager() throws Exception {
    try {
      ComposableSecurityManagerPolicy.setSystemSecurityManager();
      ComposableSecurityManagerPolicy.setDefaultPolicy(new EmptyPolicy() {
        @Override
        public void checkPropertyPermission(String action, String name) {
          if ("not.allowed".equals(name)) {
            throw new RuntimeException("nope");
          }
        }
      });

      List<Callable<Exception>> work = Arrays.asList( //
          getWork("not.allowed"), //
          getWork("allowed"), //
          getWork("not.allowed"), //
          getWork("allowed"), //
          getWork("not.allowed"), //
          getWork("allowed"));

      AccessControlContext accessControlContext = AccessController.getContext();
      List<String> exs1 = work.parallelStream().map(x -> {
        return AccessController.doPrivileged((PrivilegedAction<Exception>) () -> {
          try {
            return x.call();
          } catch (Exception e) {
            return e;
          }
        }, accessControlContext);
      }).map(e -> e != null ? e.getMessage() : null).collect(Collectors.toList());

      assertThat(exs1).containsExactly("nope", null, "nope", null, "nope", null);
    } finally {
      ComposableSecurityManagerPolicy.removeSystemSecurityManager();
    }
  }

  @Test
  public void testSystemProperties() throws Exception {
    Policy policy = new EmptyPolicy() {
      @Override
      public void checkPropertyPermission(String action, String name) {
        throw new SecurityException();
      }
    };

    ComposableSecurityManagerPolicy testee = newPolicy(policy);

    try {
      testee.implies(this.getClass().getProtectionDomain(), new PropertyPermission("*", "read"));
      fail();
    } catch (SecurityException expected) {
      // builders can't access system properties
    }

    try {
      testee.implies(this.getClass().getProtectionDomain(), new PropertyPermission("*", "read"));
      fail();
    } catch (SecurityException expected) {
      // builders can't access system properties
    }
  }

  @Test(expected = SecurityException.class)
  public void testNetworkAccess() throws Exception {
    Policy policy = new EmptyPolicy() {
      @Override
      public void checkSocketPermission() {
        throw new SecurityException();
      }
    };

    ComposableSecurityManagerPolicy testee = newPolicy(policy);

    testee.implies(this.getClass().getProtectionDomain(),
        new SocketPermission("localhost", "connect"));
  }

  @Test
  public void testComposition() throws Exception {
    Policy policy1 = new EmptyPolicy() {
      @Override
      public void checkRead(String file) {
        if ("1".equals(file)) {
          throw new SecurityException();
        }

      }
    };
    Policy policy2 = new EmptyPolicy() {
      @Override
      public void checkRead(String file) {
        if ("2".equals(file)) {
          throw new SecurityException();
        }
      }
    };

    ComposableSecurityManagerPolicy testee = newPolicy(policy1, policy2);

    try {
      testee.implies(this.getClass().getProtectionDomain(), new FilePermission("1", "read"));
      fail();
    } catch (SecurityException expected) {
      // policy 1 should fail
    }

    try {
      testee.implies(this.getClass().getProtectionDomain(), new FilePermission("2", "read"));
      fail();
    } catch (SecurityException expected) {
      // policy 2 should fail
    }

    try {
      testee.implies(this.getClass().getProtectionDomain(), new FilePermission("3", "read"));
    } catch (SecurityException expected) {
      // should not fail
      fail();
    }
  }
}
