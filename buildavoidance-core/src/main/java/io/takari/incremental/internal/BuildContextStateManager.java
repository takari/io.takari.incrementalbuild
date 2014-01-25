package io.takari.incremental.internal;

import java.io.File;
import java.util.Collection;

interface BuildContextStateManager {
  DefaultOutput associateOutput(DefaultInput input, File outputFile);

  void associate(DefaultInput input, DefaultOutput output);

  boolean isAssociatedOutput(DefaultInput input, File outputFile);

  Collection<DefaultInput> getAssociatedInputs(DefaultOutput output);

  void associateIncludedInput(DefaultInput input, File includedFile);

  boolean isProcessingRequired(DefaultInput input);
}
