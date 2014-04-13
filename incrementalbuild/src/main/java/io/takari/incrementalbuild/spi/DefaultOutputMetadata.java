package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @noinstantiate clients are not expected to instantiate this class
 */
public class DefaultOutputMetadata implements BuildContext.OutputMetadata<File> {

  final DefaultBuildContext<?> context;

  final DefaultBuildContextState state;

  final File file;

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
  public <I> Iterable<? extends InputMetadata<I>> getAssociatedInputs(Class<I> clazz) {
    return context.getAssociatedInputs(state, file, clazz);
  }

  public Collection<String> getCapabilities(String qualifier) {
    Collection<QualifiedName> capabilities = state.outputCapabilities.get(file);
    if (capabilities == null) {
      return Collections.emptyList();
    }
    Set<String> result = new LinkedHashSet<String>();
    for (QualifiedName capability : capabilities) {
      if (qualifier.equals(capability.getQualifier())) {
        result.add(capability.getLocalName());
      }
    }
    return result;
  }

  @Override
  public <V extends Serializable> V getValue(String key, Class<V> clazz) {
    return context.getResourceAttribute(file, key, true /* previous */, clazz);
  }
}
