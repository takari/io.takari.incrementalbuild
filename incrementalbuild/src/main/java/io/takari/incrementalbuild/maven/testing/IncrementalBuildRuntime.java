package io.takari.incrementalbuild.maven.testing;

import io.takari.incrementalbuild.maven.internal.MavenBuildContext;
import io.takari.incrementalbuild.spi.DefaultBuildContext;

import java.util.List;

import javax.inject.Singleton;

import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.Ignore;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

@Ignore("tells eclipse junit launcher not to look here")
class IncrementalBuildRuntime extends AbstractMojoTestCase {

  @Override
  protected void addGuiceModules(List<Module> modules) {
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(TestBuildContext.class).in(MojoExecutionScoped.class);
        bind(DefaultBuildContext.class).to(TestBuildContext.class).in(MojoExecutionScoped.class);
        bind(MavenBuildContext.class).to(TestBuildContext.class).in(MojoExecutionScoped.class);
        bind(IncrementalBuildLog.class).in(Singleton.class);
      }
    });
  }

}
