package io.takari.incrementalbuild.maven.testing;

import io.takari.incrementalbuild.workspace.MessageSink.Severity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// this is explicitly bound in IncrementalBuildRuntime.addGuiceModules
public class IncrementalBuildLog {

  private final List<File> registeredOutputs = new ArrayList<File>();

  private final List<File> deletedOutputs = new ArrayList<File>();

  private final List<File> carriedOverOutputs = new ArrayList<File>();

  private final Map<File, List<String>> resourceMessages = new HashMap<File, List<String>>();

  void addRegisterOutput(File outputFile) {
    registeredOutputs.add(outputFile);
  }

  void addDeletedOutput(File outputFile) {
    deletedOutputs.add(outputFile);
  }

  void addCarryoverOutput(File outputFile) {
    carriedOverOutputs.add(outputFile);
  }

  void message(Object resource, int line, int column, String message, Severity severity,
      Throwable cause) {

    if (!(resource instanceof File)) {
      // XXX I am too lazy right now, need to fix this later
      throw new IllegalArgumentException();
    }
    File file = (File) resource;
    String msg = String.format("%s %s [%d:%d] %s", severity.name(), //
        file.getName(), line, column, message);

    List<String> messages = resourceMessages.get(file);
    if (messages == null) {
      messages = new ArrayList<String>();
      resourceMessages.put(file, messages);
    }
    messages.add(msg);
  }

  // public api

  public Collection<File> getRegisteredOutputs() {
    return registeredOutputs;
  }

  public Collection<File> getDeletedOutputs() {
    return deletedOutputs;
  }

  public Collection<File> getCarriedOverOutputs() {
    return carriedOverOutputs;
  }

  public Collection<String> getMessages(File inputFile) {
    List<String> messages = resourceMessages.get(inputFile);
    return messages != null ? messages : Collections.<String>emptyList();
  }

  public void clear() {
    registeredOutputs.clear();
    deletedOutputs.clear();
    carriedOverOutputs.clear();
    resourceMessages.clear();
  }

}
