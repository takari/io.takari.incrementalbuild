package io.takari.incremental.maven.testing;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BuildContextLog {

  private final List<File> registeredOutputs = new ArrayList<File>();

  private final List<File> deletedOutputs = new ArrayList<File>();

  private final List<String> messages = new ArrayList<String>();

  public void addRegisterOutput(File outputFile) {
    registeredOutputs.add(outputFile);
  }

  public Collection<File> getRegisteredOutputs() {
    return registeredOutputs;
  }

  public void addDeletedOutput(File outputFile) {
    deletedOutputs.add(outputFile);
  }

  public Collection<File> getDeletedOutputs() {
    return deletedOutputs;
  }

  public void addMessage(String message) {
    messages.add(message);
  }

  public Collection<String> getMessages(File file) {
    return messages;
  }

  public void clear() {
    registeredOutputs.clear();
    deletedOutputs.clear();
    messages.clear();
  }

}
