package io.takari.incrementalbuild.cli;

import io.takari.incrementalbuild.spi.DefaultBuildContext;
import io.takari.incrementalbuild.spi.DefaultMessageSink;
import io.takari.incrementalbuild.spi.FilesystemWorkspace;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// TODO may want to rename various BuildContext implementation classes
public class StandaloneBuildContext extends DefaultBuildContext<Exception> {

  private StandaloneBuildContext() {
    super(new FilesystemWorkspace(), new DefaultMessageSink(), null, Collections
        .<String, Serializable>emptyMap());
  }

  public StandaloneBuildContext(File stateFile, String[] args) {
    super(new FilesystemWorkspace(), new DefaultMessageSink(), stateFile, toConfiguration(args));
  }

  private static Map<String, Serializable> toConfiguration(String[] args) {
    Map<String, Serializable> configuration = new HashMap<String, Serializable>();
    for (int i = 0; i < args.length; i++) {
      configuration.put("args[" + i + "]", args[i]);
    }
    return configuration;
  }

  @Override
  protected Exception newBuildFailureException(int errorCount) {
    return new Exception(errorCount + " error(s) encountered, see previous message(s) for details");
  }

  public static StandaloneBuildContext transientBuildContext() {
    return new StandaloneBuildContext();
  }
}
