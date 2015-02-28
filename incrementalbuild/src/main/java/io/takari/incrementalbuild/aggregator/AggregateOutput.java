package io.takari.incrementalbuild.aggregator;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Represents aggregate output being created.
 */
public interface AggregateOutput {

  public File getResource();

  public Iterable<File> registerInputs(File basedir, Collection<String> includes,
      Collection<String> excludes) throws IOException;

  public boolean aggregateIfNecessary() throws IOException;
}
