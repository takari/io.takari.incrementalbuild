package io.takari.incrementalbuild.internal.maven;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;

import com.google.common.base.Charsets;

@Named
@MojoExecutionScoped
public class MojoConfigurationDigester {

  private final Logger logger;
  private final MavenSession session;
  private final MavenProject project;
  private final MojoExecution execution;

  @Inject
  public MojoConfigurationDigester(MavenSession session, MavenProject project,
      MojoExecution execution, Logger logger) {
    this.session = session;
    this.project = project;
    this.execution = execution;
    this.logger = logger;
  }

  public Map<String, byte[]> digest() throws IOException {
    Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
    List<Artifact> classpath = execution.getMojoDescriptor().getPluginDescriptor().getArtifacts();
    result.put("mojo.classpath", new ClasspathDigester().digest(classpath));
    result.put("mojo.mavenProject", digestMavenProject(project));
    result.put("mojo.sessionProperties", digestSessionProperties(session));
    return result;
  }

  private byte[] digestMavenProject(MavenProject project) {
    MessageDigest digester = SHA1Digester.newInstance();

    // effective pom.xml defines project configuration, rebuild whenever project configuration
    // changes we can't be more specific here because mojo can access entire project model, not just
    // its own configuration
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    try {
      new MavenXpp3Writer().write(buf, project.getModel());
    } catch (IOException e) {
      // can't happen
    }
    return digester.digest(buf.toByteArray());
  }

  @SuppressWarnings("deprecation")
  private byte[] digestSessionProperties(MavenSession session) {

    // execution properties define build parameters passed in from command line and jvm used
    SortedMap<String, String> executionProperties = new TreeMap<String, String>();

    for (Map.Entry<Object, Object> property : session.getExecutionProperties().entrySet()) {
      // TODO unit test non-string keys do not cause problems at runtime
      // TODO test if non-string values can or cannot be used
      Object key = property.getKey();
      Object value = property.getValue();
      if (key instanceof String && value instanceof String) {
        executionProperties.put(key.toString(), value.toString());
      } else {
        if (logger.isDebugEnabled()) {
          String keyClass = key != null ? key.getClass().getName() : null;
          String valueClass = value != null ? value.getClass().getName() : null;
          logger.debug("Not a string property {}@{} = {}@{}", keyClass, key, valueClass, value);
        }
      }
    }

    // m2e workspace launch
    // executionProperties.remove( "classworlds.conf" );

    Iterator<Map.Entry<String, String>> iter = executionProperties.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<String, String> property = iter.next();

      // Environment has PID of java process (env.JAVA_MAIN_CLASS_<PID>), SSH_AGENT_PID, unique
      // TMPDIR (on OSX)
      // and other volatile variables.
      if (property.getKey().startsWith("env.")) {
        iter.remove();
      }
    }

    MessageDigest digester = SHA1Digester.newInstance();

    for (Map.Entry<String, String> property : executionProperties.entrySet()) {
      digester.update(property.getKey().getBytes(Charsets.UTF_8));
      digester.update(property.getValue().getBytes(Charsets.UTF_8));
    }

    return digester.digest();
    // final boolean changed = digest(digester, "mojo.sessionProperties", local.finish());
    // if (changed && logger.isDebugEnabled()) {
    // StringBuilder msg = new StringBuilder();
    // msg.append("Session properties changed for mojo " + execution.toString()).append('\n');
    // for (Map.Entry<String, String> property : executionProperties.entrySet()) {
    // msg.append("   ").append(property.getKey()).append('=').append(property.getValue());
    // msg.append('\n');
    // }
    // logger.debug(msg.toString());
    // }
  }

}
