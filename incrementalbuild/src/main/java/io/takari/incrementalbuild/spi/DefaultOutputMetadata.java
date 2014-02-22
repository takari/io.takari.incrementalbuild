package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;

/**
 * @noinstantiate clients are not expected to instantiate this class
 */
public class DefaultOutputMetadata
    implements
      BuildContext.OutputMetadata<File>,
      CapabilitiesProvider {

  private final DefaultBuildContext<?> context;

  private final DefaultBuildContextState state;

  private final File file;


  DefaultOutputMetadata(DefaultBuildContext<?> context, DefaultBuildContextState state, File file) {
    this.context = context;
    this.state = state;
    this.file = file;
  }

  @Override
  public File getResource() {
    return file;
  }

  @Override
  public ResourceStatus getStatus() {
    return context.getOutputStatus(file);
  }

  @Override
  public Iterable<? extends InputMetadata<File>> getAssociatedInputs() {
    return context.getAssociatedInputs(state, file);
  }

  @Override
  public Collection<String> getCapabilities(String qualifier) {
    return state.getCapabilities(file, qualifier);
  }

  @Override
  public <V extends Serializable> V getValue(String key, Class<V> clazz) {
    return state.getResourceAttribute(file, key, clazz);
  }
}
