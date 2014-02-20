package io.takari.incrementalbuild.maven.internal;

import io.takari.incrementalbuild.BuildContext;

import java.io.File;

import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.maven.execution.scope.MojoExecutionScoped;

/**
 * Singleton-friendly {@link BuildContext} implementation. Uses {@link Provider} to locate currently
 * active {@link MavenBuildContext} instance.
 * <p>
 * {@link MavenBuildContext} is {@link MojoExecutionScoped} and its lifecycle is bound to lifecycle
 * of the corresponding mojo execution, that is, it is created right before the mojo execution
 * starts and discarded immediately after the mojo execution ends. Most Maven plugin components,
 * however, are singletons, which means they are created when plugin class realm is created at the
 * beginning of the build and discarded when plugin realm is discarded at the end of the build. It
 * is therefore not possible to bind MavenBuildContext to singleton components directly.
 */
@Named(SingletonBuildContext.HINT)
@Typed(BuildContext.class)
@Singleton
public class SingletonBuildContext implements BuildContext {

  public static final String HINT = "singleton";

  private Provider<MavenBuildContext> delegate;

  @Inject
  public SingletonBuildContext(Provider<MavenBuildContext> delegate) {
    this.delegate = delegate;
  }

  @Override
  public InputMetadata<File> registerInput(File inputFile) {
    return delegate.get().registerInput(inputFile);
  }

  @Override
  public Iterable<? extends InputMetadata<File>> registerInputs(Iterable<File> inputFiles) {
    return delegate.get().registerInputs(inputFiles);
  }

  @Override
  public Iterable<? extends Input<File>> registerAndProcessInputs(Iterable<File> inputFiles) {
    return delegate.get().registerAndProcessInputs(inputFiles);
  }

  @Override
  public Output<File> processOutput(File outputFile) {
    return delegate.get().processOutput(outputFile);
  }

  @Override
  public <T> Iterable<? extends InputMetadata<T>> getRegisteredInputs(Class<T> clazz) {
    return delegate.get().getRegisteredInputs(clazz);
  }

  @Override
  public <T> Iterable<? extends OutputMetadata<T>> getProcessedOutputs(Class<T> clazz) {
    return delegate.get().getProcessedOutputs(clazz);
  }
}
