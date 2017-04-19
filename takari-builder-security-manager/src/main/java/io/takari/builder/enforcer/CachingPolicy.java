package io.takari.builder.enforcer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class CachingPolicy {

  final Policy policy;

  private final Set<String> readCache = ConcurrentHashMap.newKeySet();

  private final Set<String> writeCache = ConcurrentHashMap.newKeySet();

  public CachingPolicy(Policy policy) {
    this.policy = policy;
  }

  public void checkSocketPermission() {
    policy.checkSocketPermission();
  }

  public void checkPropertyPermission(String action, String name) {
    policy.checkPropertyPermission(action, name);
  }

  public void checkExec(String cmd) {
    policy.checkExec(cmd);
  }

  public void checkRead(String file) {
    if (readCache.add(file)) {
      policy.checkRead(file);
    }
  }

  public void checkWrite(String file) {
    if (writeCache.add(file)) {
      policy.checkWrite(file);
    }
  }
}
