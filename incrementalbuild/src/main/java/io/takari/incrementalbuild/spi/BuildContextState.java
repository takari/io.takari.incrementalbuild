package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.OutputMetadata;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;

interface BuildContextState {

  Iterable<? extends InputMetadata<File>> getAssociatedInputs(File file);

  Iterable<? extends OutputMetadata<File>> getAssociatedOutputs(File file);

  Collection<String> getOutputCapabilities(File outputFile, String qualifier);

  <V extends Serializable> V getResourceAttribute(File resource, String key, Class<V> clazz);

}
