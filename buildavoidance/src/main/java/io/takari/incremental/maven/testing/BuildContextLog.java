package io.takari.incremental.maven.testing;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BuildContextLog {

  private final List<File> registeredOutputs = new ArrayList<File>();

  private final List<String> messages = new ArrayList<String>();

  public void addRegisterOutput(File outputFile) {
    registeredOutputs.add(outputFile);
  }

  public Collection<File> getRegisteredOutputs() {
    return registeredOutputs;
  }

  public Collection<String> getMessages(File file) {
    return messages;
  }

  public void clear() {
    registeredOutputs.clear();
    messages.clear();
  }

}
