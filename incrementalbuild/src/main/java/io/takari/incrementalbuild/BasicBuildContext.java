package io.takari.incrementalbuild;

import java.io.File;

/**
 * Build context that tracks inputs and outputs but not associations among them.
 * <p>
 * If there are new, changed or removed inputs, all outputs must be recreated. Outputs that are not
 * recreated are considered obsolete and will be deleted at the end of the build.
 * <p>
 */
public interface BasicBuildContext {
  public ResourceMetadata<File> registerInput(File inputFile);

  public boolean isProcessingRequired();

  public Output<File> processOutput(File outputFile);
}
