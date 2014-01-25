package io.takari.incremental.internal;

import java.io.File;
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

}
