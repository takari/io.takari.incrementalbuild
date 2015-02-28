package io.takari.incrementalbuild.maven.internal;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.Resource;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.spi.BuildContextEnvironment;
import io.takari.incrementalbuild.spi.DefaultBuildContext;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.maven.execution.scope.MojoExecutionScoped;

@Named
public class MavenBuildContext implements BuildContext {

  @Named
  @Typed(MojoExecutionScopedBuildContext.class)
  @MojoExecutionScoped
  public static class MojoExecutionScopedBuildContext extends DefaultBuildContext {
    @Inject
    public MojoExecutionScopedBuildContext(BuildContextEnvironment configuration) {
      super(configuration);
    }
  }

  private final Provider<MojoExecutionScopedBuildContext> provider;

  @Inject
  public MavenBuildContext(Provider<MojoExecutionScopedBuildContext> delegate) {
    this.provider = delegate;
  }

  @Override
  public ResourceMetadata<File> registerInput(File inputFile) {
    return provider.get().registerInput(inputFile);
  }

  @Override
  public Iterable<? extends ResourceMetadata<File>> registerInputs(File basedir,
      Collection<String> includes, Collection<String> excludes) throws IOException {
    return provider.get().registerInputs(basedir, includes, excludes);
  }

  @Override
  public Iterable<? extends Resource<File>> registerAndProcessInputs(File basedir,
      Collection<String> includes, Collection<String> excludes) throws IOException {
    return provider.get().registerAndProcessInputs(basedir, includes, excludes);
  }

  @Override
  public void markSkipExecution() {
    provider.get().markSkipExecution();
  }
}
