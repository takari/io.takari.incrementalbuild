package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.OutputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.File;
import java.io.Serializable;

/**
 * @noinstantiate clients are not expected to instantiate this class
 */
public class DefaultInputMetadata implements InputMetadata<File> {

  final DefaultBuildContext<?> context;

  private final DefaultBuildContextState state;

  private final File file;

  DefaultInputMetadata(DefaultBuildContext<?> context, DefaultBuildContextState state, File file) {
    this.context = context;
    this.state = state;
    this.file = file;
  }

  @Override
  public File getResource() {
    return file;
  }

  @Override
  public Iterable<? extends OutputMetadata<File>> getAssociatedOutputs() {
    return context.getAssociatedOutputs(state, file);
  }

  @Override
  public ResourceStatus getStatus() {
    return context.getInputStatus(file);
  }

  @Override
  public <V extends Serializable> V getValue(String key, Class<V> clazz) {
    return state.getResourceAttribute(file, key, clazz);
  }

  @Override
  public DefaultInput process() {
    return context.processInput(this);
  }

}
