package io.takari.incremental.maven.testing;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuildContextLog {

  private final List<File> registeredOutputs = new ArrayList<File>();

  private final List<File> deletedOutputs = new ArrayList<File>();

  private final Map<File, List<String>> inputMessages = new HashMap<File, List<String>>();

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

  public void addMessage(File inputFile, String message) {
    List<String> messages = inputMessages.get(inputFile);
    if (messages == null) {
      messages = new ArrayList<String>();
      inputMessages.put(inputFile, messages);
    }
    messages.add(message);
  }

  public Collection<String> getMessages(File inputFile) {
    List<String> messages = inputMessages.get(inputFile);
    return messages != null ? messages : Collections.<String>emptyList();
  }

  public void clear() {
    registeredOutputs.clear();
    deletedOutputs.clear();
    inputMessages.clear();
  }

}
