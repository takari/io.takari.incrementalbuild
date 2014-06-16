package io.takari.incrementalbuild.maven.testing;

import io.takari.incrementalbuild.workspace.MessageSink;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// this is explicitly bound in IncrementalBuildRuntime.addGuiceModules
public class IncrementalBuildLog implements MessageSink {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final List<File> registeredOutputs = new ArrayList<File>();

  private final List<File> deletedOutputs = new ArrayList<File>();

  private final List<File> carriedOverOutputs = new ArrayList<File>();

  private final Map<File, List<String>> inputMessages = new HashMap<File, List<String>>();

  private int errorCount;

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

  public void addCarryoverOutput(File outputFile) {
    carriedOverOutputs.add(outputFile);
  }

  public Collection<File> getCarriedOverOutputs() {
    return carriedOverOutputs;
  }

  public Collection<String> getMessages(File inputFile) {
    List<String> messages = inputMessages.get(inputFile);
    return messages != null ? messages : Collections.<String>emptyList();
  }

  public void clear() {
    registeredOutputs.clear();
    deletedOutputs.clear();
    carriedOverOutputs.clear();
    inputMessages.clear();
    errorCount = 0;
  }

  @Override
  public void message(Object resource, int line, int column, String message, Severity severity,
      Throwable cause) {

    if (!(resource instanceof File)) {
      // XXX I am too lazy right now, need to fix this later
      throw new IllegalArgumentException();
    }
    File file = (File) resource;
    String msg = String.format("%s %s [%d:%d] %s", severity.name(), //
        file.getName(), line, column, message);

    List<String> messages = inputMessages.get(file);
    if (messages == null) {
      messages = new ArrayList<String>();
      inputMessages.put(file, messages);
    }
    messages.add(msg);

    if (severity == MessageSink.Severity.ERROR) {
      errorCount++;
    }

    switch (severity) {
      case ERROR:
        log.error(msg);
        break;
      case WARNING:
        log.warn(msg);
        break;
      case INFO:
        log.info(msg);
        break;
    }
  }

  @Override
  public MessageSink replayMessageSink() {
    return this;
  }

  @Override
  public int getErrorCount() {
    return errorCount;
  }

  @Override
  public void clearMessages(Object resource) {}
}
