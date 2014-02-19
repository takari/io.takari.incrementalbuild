package io.takari.incrementalbuild.maven.testing;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.maven.internal.MavenBuildContext;
import io.takari.incrementalbuild.spi.DefaultBuildContext;

import java.util.List;

import javax.inject.Singleton;

import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.execution.scope.internal.MojoExecutionScope;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.PlexusConstants;
import org.junit.Ignore;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

@Ignore("tells eclipse junit launcher not to look here")
class BuildAvoidanceRuntime extends AbstractMojoTestCase {

  @Override
  protected void addGuiceModules(List<Module> modules) {
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        // execution scope bindings (core binds these in plugin realm injector only)
        MojoExecutionScope executionScope = new MojoExecutionScope();
        bindScope(MojoExecutionScoped.class, executionScope);
        bind(MojoExecutionScope.class).toInstance(executionScope);
        bind(MavenProject.class).toProvider(MojoExecutionScope.<MavenProject>seededKeyProvider())
            .in(executionScope);
        bind(MojoExecution.class).toProvider(MojoExecutionScope.<MojoExecution>seededKeyProvider())
            .in(executionScope);

        // instance bindings
        bind(TestBuildContext.class).in(executionScope);
        bind(BuildContext.class).to(TestBuildContext.class).in(executionScope);
        bind(DefaultBuildContext.class).to(TestBuildContext.class).in(executionScope);
        bind(MavenBuildContext.class).to(TestBuildContext.class).in(executionScope);
        bind(BuildContextLog.class).in(Singleton.class);
      }
    });
  }

  @Override
  protected ContainerConfiguration setupContainerConfiguration() {
    ContainerConfiguration configuration = super.setupContainerConfiguration();
    configuration.setClassPathScanning(PlexusConstants.SCANNING_INDEX).setAutoWiring(true);
    return configuration;
  }

}
