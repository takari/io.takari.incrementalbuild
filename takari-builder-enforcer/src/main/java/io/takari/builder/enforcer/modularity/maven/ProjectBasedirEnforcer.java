package io.takari.builder.enforcer.modularity.maven;

import java.io.File;
import java.io.IOException;

import io.takari.builder.enforcer.internal.EnforcerConfig;

public interface ProjectBasedirEnforcer {

  void writeConfiguration(File file) throws IOException;

  void replayLog(File file) throws IOException;

  void enterExecPrivileged();

  void leaveExecPrivileged();

  boolean isEnabledForProject(EnforcerConfig config, String artifactId);

}
