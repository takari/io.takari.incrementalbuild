package io.takari.builder.enforcer.modularity.maven.internal;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;

import io.takari.builder.enforcer.internal.EnforcerConfig;
import io.takari.builder.enforcer.modularity.internal.SessionConfig;

@Named
@SessionScoped
class ModularityEnforcerSessionState {

  private static final String ENFORCER_CONFIG_FILE_LOCATION = ".mvn/basedir-enforcer.config";

  private final SessionConfig sessionConfig;
  private final String topleveldir;
  private final EnforcerConfig enforcerConfig;

  @Inject
  ModularityEnforcerSessionState(MavenSession session) {
    this.sessionConfig = new SessionConfig(session);
    File multimoduleDir = session.getRequest().getMultiModuleProjectDirectory();
    this.topleveldir = multimoduleDir != null ? multimoduleDir.getAbsolutePath() : null;
    this.enforcerConfig = multimoduleDir != null
        ? EnforcerConfig.fromFile(session.getRequest().getMultiModuleProjectDirectory().toPath()
            .resolve(ENFORCER_CONFIG_FILE_LOCATION))
        : EnforcerConfig.empty();
  }

  SessionConfig getSessionConfig() {
    return sessionConfig;
  }

  EnforcerConfig getEnforcerConfig() {
    return enforcerConfig;
  }

  String getTopleveldir() {
    return topleveldir;
  }

  boolean isEnforcerEnabled() {
    return !sessionConfig.isDisabled();
  }

}
