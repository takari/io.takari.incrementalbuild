package io.takari.incrementalbuild.aggregator;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

/**
 * Represents input set being aggregated.
 */
public interface InputSet {

  public Iterable<File> addInputs(File basedir, Collection<String> includes,
      Collection<String> excludes) throws IOException;

  public boolean aggregateIfNecessary(File outputFile, InputAggregator aggregator)
      throws IOException;

  public <T extends Serializable> boolean aggregateIfNecessary(File outputFile,
      MetadataAggregator<T> aggregator) throws IOException;

}
