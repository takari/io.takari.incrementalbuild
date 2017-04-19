package io.takari.builder.enforcer;

import java.io.FilePermission;
import java.net.SocketPermission;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PropertyPermission;
import java.util.concurrent.ForkJoinPool;

import io.takari.builder.enforcer.SimpleFilePermission.FileReadPermission;
import io.takari.builder.enforcer.SimpleFilePermission.FileWritePermission;


public class ComposableSecurityManagerPolicy extends java.security.Policy {

  /**
   * So we can set the jvm back to the state we found it.
   */
  private final SecurityManager originalManager;
  
  /**
   * So we can set the jvm back to the state we found it.
   */
  private final java.security.Policy originalPolicy;
  
  /**
   * used for forked jvms and threads that might not otherwise be restricted.
   */
  private volatile List<CachingPolicy> defaultPolicy;
  
  /**
   * the set of policies with configured contexts that will be used in the default case.
   */
  private final ThreadLocal<Map<Object, CachingPolicy>> contextPolicies = new ThreadLocal<>();
  
  /**
   * The policy, so that it is accessible without calling Policy.getPolicy().
   */
  private volatile static ComposableSecurityManagerPolicy policy;
  
  /**
   * a little trick to ensure our code can do privileged actions without endlessly calling itself
   */
  private final ThreadLocal<Boolean> privileged = ThreadLocal.withInitial(() -> Boolean.FALSE);
  
  private static ComposableSecurityManagerPolicy setPolicy(SecurityManager originalManager) {
    Policy originalPolicy = Policy.getPolicy();
    if (originalPolicy instanceof ComposableSecurityManagerPolicy) {
      throw new IllegalStateException("Composable security manager policy has already been set.");
    }
    policy = new ComposableSecurityManagerPolicy(originalManager, originalPolicy);
    Policy.setPolicy(policy);
    return policy;
  }
  
  public ComposableSecurityManagerPolicy(SecurityManager originalManager, java.security.Policy originalPolicy) {
    this.originalManager = originalManager;
    this.originalPolicy = originalPolicy;
  }
  
  /**
   * The key to using a Policy rather than a modified security manager is that a Policy will
   * not be called when the jvm code (which does not have a protection domain associated with it)
   * executes command to get system properties of spawns threads for itself.   We can further
   * benefit from the optimizations this gives us by returning a permission collection 
   * {@link #allPermissions} when {@link #getPermissions(ProtectionDomain)} is called with a 
   * protection domain we don't want to interfere with.
   */
  @Override
  public boolean implies(ProtectionDomain protectionDomain, Permission permission) {
    if (privileged.get() == Boolean.TRUE) {
      return true;
    }
    if ("setSecurityManager".equals(permission.getName())
        || "setPolicy".equals(permission.getName())) {
      return false; // only our code can set system security manager and policy
    }
    try {
      privileged.set(Boolean.TRUE);
      return enforce(permission); // legacy/unmanaged runtime support
    } finally {
      privileged.set(Boolean.FALSE);
    }
  }

  private boolean enforce(Permission permission) {
    //ignore updates to the collection past this point for this invocation of implies().
    Collection<CachingPolicy> policies = policies();
    /*this prevents the initial load of all the classes needed to do the work of setting up the security manager from overflowing the buffer.
    each class load would have quite a few more calls added in to it by our security manager.
    recommend we remove this when we can determine which protectionDomains we don't want to interfere with. */
    if (policies.isEmpty()) return true;
    if (permission instanceof FileReadPermission) {
      checkRead(policies, permission.getName());
    } else if (permission instanceof FileWritePermission) {
      checkWrite(policies, permission.getName());
    } else if (permission instanceof FilePermission) {
      String fileName = permission.getName();
      for(String actionSting : ((FilePermission) permission).getActions().split(",")) {
        switch (actionSting) {
          case "readlink":
          case "read": checkRead(policies, fileName); break;
          case "delete":
          case "write": checkWrite(policies, fileName); break;
          case "execute": policies.forEach(p -> p.checkExec(fileName)); break;
        }
      }
    } else if (permission instanceof PropertyPermission) {
      policies.forEach(p -> p.checkPropertyPermission(permission.getActions(), permission.getName()));
    } else if (permission instanceof SocketPermission) {
      policies.forEach(p -> p.checkSocketPermission());
    } 
    //if something bad was done the policy could have recorded the naughty deeds in it's context, and break the build after the fact when the context closes.
    return true;
  }

  protected void checkWrite(Collection<CachingPolicy> policies,
      String fileName) {
    policies.forEach(p -> p.checkWrite(fileName));
  }

  protected void checkRead(Collection<CachingPolicy> policies,
      String fileName) {
    policies.forEach(p -> p.checkRead(fileName));
  }
  
  
  
  public static final PermissionCollection allPermissions;
  static {
    allPermissions = new AllPermission().newPermissionCollection();
    allPermissions.add(new AllPermission());
    allPermissions.setReadOnly();
  }
  
  @Override
  public PermissionCollection getPermissions(ProtectionDomain domain) {
    System.out.println("Getting permissions domain :" +domain.getCodeSource());
    //TODO:  when we can determine maven/vs non maven managed classloaders we can return allpermissions here.
    return Policy.UNSUPPORTED_EMPTY_COLLECTION;
  }
  
  @Override
  public PermissionCollection getPermissions(CodeSource domain) {
    System.out.println("Getting permissions codesource :" +domain);
    return Policy.UNSUPPORTED_EMPTY_COLLECTION;
  }
  
  
  
  //POLICY management.
  
  private static ComposableSecurityManagerPolicy get() {

    if (policy == null) {
      throw new IllegalStateException("Illegal System SecurityManager");
    }

    return policy;
  }
  
  
  private Collection<CachingPolicy> policies() {
    Map<Object, CachingPolicy> policies = contextPolicies.get();
    if (policies != null) {
      return policies.values();
    }
    if (defaultPolicy != null) {
      return defaultPolicy;
    }
    return Collections.emptyList();
  }
  
  static Map<Object, CachingPolicy> getContextPolicies() {
    Map<Object, CachingPolicy> policies = null;
    if (policy != null) {
      policies = policy.contextPolicies.get();
    }
    return policies != null ? new LinkedHashMap<>(policies) : null;
  }

  static void setContextPolicies(Map<Object, CachingPolicy> policies) {
    if (policy != null) {
      if (policies != null && !policies.isEmpty()) {
        policy.contextPolicies.set(new LinkedHashMap<>(policies));
      } else {
        policy.contextPolicies.set(null);
      }
    }
  }

  //
  // Policy registration/unregistration
  //

  void registerPolicy(Object key, io.takari.builder.enforcer.Policy policy) {
    Map<Object, CachingPolicy> policies = contextPolicies.get();

    if (policies != null && policies.containsKey(key)) {
      throw new IllegalArgumentException("Policy has already been registered.");
    }

    if (policies == null) {
      policies = new LinkedHashMap<>();
      contextPolicies.set(policies);
    }

    policies.put(key, new CachingPolicy(policy));
  }
  
  io.takari.builder.enforcer.Policy unregisterPolicy(Object key) {
    Map<Object, CachingPolicy> policies = contextPolicies.get();

    if (policies == null || !policies.containsKey(key)) {
      throw new IllegalArgumentException("Policy has not been registered.");
    }

    CachingPolicy cachingPolicy = policies.remove(key);
    if (policies.size() == 0) {
      contextPolicies.set(null);
    }

    return cachingPolicy != null? cachingPolicy.policy: null;
  }

  io.takari.builder.enforcer.Policy getPolicy(Object key) {
    Map<Object, CachingPolicy> policies = contextPolicies.get();
    if (policies == null) {
      return null;
    }
    CachingPolicy cachingPolicy = policies.get(key);
    return cachingPolicy != null ? cachingPolicy.policy : null;
  }

  public static void setDefaultPolicy(io.takari.builder.enforcer.Policy policy) {
    ComposableSecurityManagerPolicy manager = get();

    if (manager.defaultPolicy != null && policy != null) {
      throw new IllegalArgumentException("Default Policy has already been set.");
    }

    manager.defaultPolicy =
        policy != null ? Collections.singletonList(new CachingPolicy(policy)) : null;
  }
  
  public static void registerContextPolicy(Object key, io.takari.builder.enforcer.Policy policy) {
    get().registerPolicy(key, policy);
  }

  public static io.takari.builder.enforcer.Policy unregisterContextPolicy(Object key) {
    return get().unregisterPolicy(key);
  }

  public static io.takari.builder.enforcer.Policy getContextPolicy(Object key) {
    return get().getPolicy(key);
  }
  
  
  public static ComposableSecurityManagerPolicy setSystemSecurityManager() {
    SecurityManager originalManager = System.getSecurityManager();
    if (originalManager instanceof ExecCommandPassingSecurityManager) {
      throw new IllegalStateException("System SecurityManager has already been set.");
    }

    /*
     * When security manager is installed, ForkJoinPool used by parallel stream uses
     * "extra-conservative safe-out-of-the-box" innocuous worker threads that do not have any
     * permissions. Although "safe", this proves to be too conservative for builder runtime, where
     * most builders require access to system resources and therefore assume non-secured
     * environment.
     *
     * We already assume we are running in insecure environment (we would not be able to install our
     * security manager and policy otherwise), so we prime ForkJoinPool common pool before we setup
     * security manager. This way we preserve pool's thread factory as it was (or would be) without
     * our security manager installed. If the pool was already using innocuous worker threads, that
     * means builders already had means to deal with it. Otherwise the pool will use regular worker
     * threads that have access to system resources.
     * 
     * @see http://mail.openjdk.java.net/pipermail/core-libs-dev/2013-December/024044.html
     */
    ForkJoinPool.commonPool();
    
    ComposableSecurityManagerPolicy newPolicy = ComposableSecurityManagerPolicy.setPolicy(originalManager);
    System.setSecurityManager(new ExecCommandPassingSecurityManager());
    return newPolicy;
  }

  public static ComposableSecurityManagerPolicy removeSystemSecurityManager() {
    ComposableSecurityManagerPolicy manager = get();
    try {
      manager.privileged.set(Boolean.TRUE);
      System.setSecurityManager(manager.originalManager);
      Policy.setPolicy(manager.originalPolicy);
      policy = null;
    } finally {
      manager.privileged.set(Boolean.FALSE);
    }
    return manager;
  }
  
}
