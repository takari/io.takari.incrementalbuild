package io.takari.incremental.maven.testing;

import java.io.File;
import java.util.Collection;

public interface BuildContextLog {

  public Collection<File> getUpdatedOutputs();

  public Collection<String> getMessages(File file);

  public void clear();

}
