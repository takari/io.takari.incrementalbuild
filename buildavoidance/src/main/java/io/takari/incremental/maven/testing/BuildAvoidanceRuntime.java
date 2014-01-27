package io.takari.incremental.maven.testing;

import io.takari.incremental.BuildContext;

import java.util.List;

import javax.inject.Singleton;

import org.apache.maven.execution.MavenSession;
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
        MojoExecutionScope scope = new MojoExecutionScope();

        bindScope(MojoExecutionScoped.class, scope);
        bind(MojoExecutionScope.class).toInstance(scope);

        bind(MavenSession.class).toProvider(MojoExecutionScope.<MavenSession>seededKeyProvider())
            .in(scope);
        bind(MavenProject.class).toProvider(MojoExecutionScope.<MavenProject>seededKeyProvider())
            .in(scope);
        bind(MojoExecution.class).toProvider(MojoExecutionScope.<MojoExecution>seededKeyProvider())
            .in(scope);

        bind(BuildContext.class).to(TestBuildContext.class).in(scope);
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
