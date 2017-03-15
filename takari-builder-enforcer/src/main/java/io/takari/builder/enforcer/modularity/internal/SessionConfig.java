package io.takari.builder.enforcer.modularity.internal;

import org.apache.maven.execution.MavenSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionConfig {

  private final Logger log = LoggerFactory.getLogger("SPRINKLING");

  private static final String DISABLED_PROPERTY_NAME = "modularity.enforcer.disabled";

  private static final String LOG_ONLY_PROPERTY_NAME = "modularity.enforcer.logonly";

  static final String ALLOW_BREAKING_PROPERTY_NAME =
      "modularity.enforcer.allow.breaking.exceptions";

  static final String ALLOW_READ_BY_DEFAULT_PROPERTY_NAME =
      "modularity.enforcer.allow.read.default";

  static final String ALLOW_WRITE_BY_DEFAULT_PROPERTY_NAME =
      "modularity.enforcer.allow.write.default";

  private final boolean disabled;

  private final boolean isLogOnly;

  private final boolean isAllowBreakingRules;

  private final boolean isReadByDefault;

  private final boolean isWriteByDefault;


  private boolean readProperty(final MavenSession session, String propertyName) {
    String system = session.getSystemProperties().getProperty(propertyName);
    String user = session.getUserProperties().getProperty(propertyName);
    String props = session.getTopLevelProject().getProperties().getProperty(propertyName);
    return Boolean.valueOf(user) || Boolean.valueOf(system) || Boolean.valueOf(props);
  }

  public SessionConfig(MavenSession session) {
    disabled = readProperty(session, DISABLED_PROPERTY_NAME);
    isLogOnly = readProperty(session, LOG_ONLY_PROPERTY_NAME);
    isAllowBreakingRules = readProperty(session, ALLOW_BREAKING_PROPERTY_NAME);
    isReadByDefault =
        isAllowBreakingRules && readProperty(session, ALLOW_READ_BY_DEFAULT_PROPERTY_NAME);
    isWriteByDefault =
        isAllowBreakingRules && readProperty(session, ALLOW_WRITE_BY_DEFAULT_PROPERTY_NAME);
  }

  public void logStatus() {
    if (disabled || isLogOnly) {
      log.warn("Modularity Enforcer is disabled: don't trust this build");
      if (isAllowBreakingRules) {
        log.info("Modularity Enforcer property " + ALLOW_BREAKING_PROPERTY_NAME
            + " is true, rules may break modularity: not all modularity errors are displayed");
      }
      if (isReadByDefault) {
        log.info("Modularity Enforcer property " + ALLOW_READ_BY_DEFAULT_PROPERTY_NAME
            + " is true, non project files will be readable by default: not all read errors are displayed");
      }
      if (isWriteByDefault) {
        log.info("Modularity Enforcer property " + ALLOW_WRITE_BY_DEFAULT_PROPERTY_NAME
            + " is true, non project files will be writeable by default: not all write errors are displayed");
      }
    } else {
      log.info("Modularity Enforcer is enabled");
      if (isAllowBreakingRules) {
        log.info("Modularity Enforcer property " + ALLOW_BREAKING_PROPERTY_NAME
            + " is true, rules may break modularity: not all modularity errors are displayed: don't trust this build!!!");
      }
      if (isReadByDefault) {
        log.info("Modularity Enforcer property " + ALLOW_READ_BY_DEFAULT_PROPERTY_NAME
            + " is true, non project files will be readable by default: don't trust this build!!!");
      }
      if (isWriteByDefault) {
        log.info("Modularity Enforcer property " + ALLOW_WRITE_BY_DEFAULT_PROPERTY_NAME
            + " is true, non project files will be writeable by default: don't trust this build!!!");
      }
    }
  }

  public boolean isDisabled() {
    return disabled;
  }

  public boolean isLogOnly() {
    return isLogOnly;
  }

  public boolean isAllowBreakingRules() {
    return isAllowBreakingRules;
  }

  public boolean isReadByDefault() {
    return isReadByDefault;
  }

  public boolean isWriteByDefault() {
    return isWriteByDefault;
  }

}
