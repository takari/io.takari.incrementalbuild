package io.takari.incrementalbuild.maven.internal;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

@Named
@MojoExecutionScoped
public class MojoConfigurationDigester {

  private final ClasspathDigester classpathDigester;

  private final MavenSession session;
  private final MojoExecution execution;

  @Inject
  public MojoConfigurationDigester(MavenSession session, MojoExecution execution) {
    this.session = session;
    this.execution = execution;
    this.classpathDigester = new ClasspathDigester(session);
  }

  public Map<String, Serializable> digest() throws IOException {
    Map<String, Serializable> result = new LinkedHashMap<String, Serializable>();

    List<Artifact> classpath = execution.getMojoDescriptor().getPluginDescriptor().getArtifacts();
    result.put("mojo.classpath", classpathDigester.digest(classpath));

    Xpp3Dom dom = execution.getConfiguration();
    if (dom != null) {
      PlexusConfiguration configuration = new XmlPlexusConfiguration(dom);
      ExpressionEvaluator evaluator = new PluginParameterExpressionEvaluator(session, execution);
      for (PlexusConfiguration child : configuration.getChildren()) {
        String name = fromXML(child.getName());
        Field field = getField(execution.getMojoDescriptor().getImplementationClass(), name);
        if (field != null) {
          try {
            String expression = child.getValue(null);
            if (expression == null) {
              expression = child.getAttribute("default-value");
            }
            if (expression != null) {
              Object value = evaluator.evaluate(expression);
              if (value != null) {
                Serializable digest = Digesters.digest(field, value);
                if (digest != null) {
                  result.put("mojo.parameter." + name, digest);
                }
              }
            }
          } catch (ExpressionEvaluationException e) {
            // TODO decide what to do about it, if anything
          }
        }
      }
    }
    return result;
  }

  private Field getField(Class<?> clazz, String name) {
    for (Field field : clazz.getDeclaredFields()) {
      if (name.equals(field.getName())) {
        return field;
      }
    }
    if (clazz.getSuperclass() != null) {
      return getField(clazz.getSuperclass(), name);
    }
    return null;
  }

  // first-name --> firstName, see
  // org.codehaus.plexus.component.configurator.converters.AbstractConfigurationConverter.fromXML(String)
  protected String fromXML(final String elementName) {
    return StringUtils.lowercaseFirstLetter(StringUtils.removeAndHump(elementName, "-"));
  }
}
