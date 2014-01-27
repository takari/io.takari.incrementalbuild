package io.takari.incremental.spi;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;

interface BuildContextStateManager {

  // direct associations

  DefaultOutput associateOutput(DefaultInput input, File outputFile);

  void associate(DefaultInput input, DefaultOutput output);

  boolean isAssociatedOutput(DefaultInput input, File outputFile);

  Collection<DefaultInput> getAssociatedInputs(DefaultOutput output);

  void associateIncludedInput(DefaultInput input, File includedFile);

  boolean isProcessingRequired(DefaultInput input);

  // required/provided capabilities

  void addRequirement(DefaultInput input, String qualifier, String localName);

  Iterable<DefaultInput> getDependentInputs(String qualifier, String localName);

  void addCapability(DefaultOutput output, String qualifier, String localName);

  Collection<String> getCapabilities(DefaultOutput output, String qualifier);

  // simple key/value pairs

  <T extends Serializable> void setValue(DefaultInput input, String key, T value);

  <T extends Serializable> T getValue(DefaultInput input, String key, Class<T> clazz);

  void addMessage(DefaultInput defaultInput, int line, int column, String message, int severity,
      Throwable cause);
}
