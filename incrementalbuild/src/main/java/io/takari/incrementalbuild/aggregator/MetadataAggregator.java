package io.takari.incrementalbuild.aggregator;

import io.takari.incrementalbuild.Output;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

public interface MetadataAggregator<T extends Serializable> {
  public Map<String, T> glean(File input) throws IOException;

  public void aggregate(Output<File> output, Map<String, T> metadata) throws IOException;
}
