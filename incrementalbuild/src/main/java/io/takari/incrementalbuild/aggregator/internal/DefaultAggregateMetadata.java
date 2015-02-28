package io.takari.incrementalbuild.aggregator.internal;

import io.takari.incrementalbuild.aggregator.AggregateOutput;
import io.takari.incrementalbuild.aggregator.InputAggregator;
import io.takari.incrementalbuild.aggregator.MetadataAggregator;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class DefaultAggregateMetadata implements AggregateOutput {

  private final DefaultAggregatorBuildContext context;
  private final File outputFile;
  private final InputAggregator inputAggregator;
  private MetadataAggregator<?> metadataAggregator;

  DefaultAggregateMetadata(DefaultAggregatorBuildContext context, File outputFile,
      InputAggregator inputAggregator) {
    this.context = context;
    this.outputFile = outputFile;
    this.inputAggregator = inputAggregator;
    this.metadataAggregator = null;
  }

  public DefaultAggregateMetadata(DefaultAggregatorBuildContext context, File outputFile,
      MetadataAggregator<?> metadataAggregator) {
    this.context = context;
    this.outputFile = outputFile;
    this.inputAggregator = null;
    this.metadataAggregator = metadataAggregator;
  }

  @Override
  public Iterable<File> registerInputs(File basedir, Collection<String> includes,
      Collection<String> excludes) throws IOException {
    return context.associateInputs(outputFile, basedir, includes, excludes, metadataAggregator);
  }

  @Override
  public boolean aggregateIfNecessary() throws IOException {
    if (inputAggregator != null) {
      return context.aggregateIfNecessary(outputFile, inputAggregator);
    }
    return context.aggregateIfNecessary(outputFile, metadataAggregator);
  }

  @Override
  public File getResource() {
    return outputFile;
  }
}
