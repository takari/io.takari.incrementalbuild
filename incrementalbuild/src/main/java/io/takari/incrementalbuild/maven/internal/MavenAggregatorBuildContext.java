package io.takari.incrementalbuild.maven.internal;

import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputSet;
import io.takari.incrementalbuild.aggregator.internal.DefaultAggregatorBuildContext;
import io.takari.incrementalbuild.spi.BuildContextEnvironment;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.apache.maven.execution.scope.MojoExecutionScoped;

@Named
public class MavenAggregatorBuildContext implements AggregatorBuildContext {

  @Named
  @Typed(MojoExecutionScopedAggregatorBuildContext.class)
  @MojoExecutionScoped
  public static class MojoExecutionScopedAggregatorBuildContext
      extends DefaultAggregatorBuildContext {
    @Inject
    public MojoExecutionScopedAggregatorBuildContext(BuildContextEnvironment configuration) {
      super(configuration);
    }
  }

  private final Provider<MojoExecutionScopedAggregatorBuildContext> provider;

  @Inject
  public MavenAggregatorBuildContext(Provider<MojoExecutionScopedAggregatorBuildContext> provider) {
    this.provider = provider;
  }

  @Override
  public InputSet newInputSet() {
    return provider.get().newInputSet();
  }

}
