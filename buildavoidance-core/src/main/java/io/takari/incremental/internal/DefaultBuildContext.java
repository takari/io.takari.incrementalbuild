package io.takari.incremental.internal;

import io.takari.incremental.BuildContext;
import io.takari.incremental.FileSet;
import io.takari.incremental.FileSetBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultBuildContext implements BuildContext {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final File stateFile;

  private final BuildContextState oldState;

  private final Map<String, byte[]> configuration;

  /**
   * Previous build state does not exist, cannot be read or configuration has changed. When
   * escalated, all input files are considered require processing.
   */
  private final boolean escalated;

  /**
   * Inputs that require processing.
   */
  private final ConcurrentMap<File, DefaultInput> inputs =
      new ConcurrentHashMap<File, DefaultInput>();

  /**
   * Maps requirement qname to all input that require it.
   */
  private final Map<QualifiedName, Set<DefaultInput>> requirementInputs =
      new HashMap<QualifiedName, Set<DefaultInput>>();

  public DefaultBuildContext(File stateFile, Map<String, byte[]> configuration) {
    // preconditions
    if (stateFile == null) {
      throw new NullPointerException();
    }
    if (configuration == null) {
      throw new NullPointerException();
    }

    this.stateFile = stateFile;
    this.oldState = load(stateFile);

    // TODO clone byte arrays too?
    this.configuration = new HashMap<String, byte[]>(configuration);

    this.escalated = getEscalated();
  }

  private boolean getEscalated() {
    if (oldState == null) {
      log.debug("No previous build state {}", stateFile);
      return true;
    }

    Map<String, byte[]> oldConfiguration = oldState.getConfiguration();

    if (!oldConfiguration.keySet().equals(configuration.keySet())) {
      log.debug("Inconsistent configuration keys, old={}, new={}", oldConfiguration.keySet(),
          configuration.keySet());
      return true;
    }

    Set<String> keys = new TreeSet<String>();
    for (String key : oldConfiguration.keySet()) {
      if (!Arrays.equals(oldConfiguration.get(key), configuration.get(key))) {
        keys.add(key);
      }
    }

    if (!keys.isEmpty()) {
      log.debug("Configuration changed, changed keys={}", keys);
      return true;
    }

    // XXX need a way to debug detailed configuration of changed keys

    return false;
  }

  private BuildContextState load(File stateFile) {
    try {
      ObjectInputStream is =
          new ObjectInputStream(new BufferedInputStream(new FileInputStream(stateFile)));
      try {
        return (BuildContextState) is.readObject();
      } finally {
        try {
          is.close();
        } catch (IOException e) {
          // ignore secondary exceptions
        }
      }
    } catch (FileNotFoundException e) {
      // this is expected, ignore
    } catch (Exception e) {
      log.debug("Could not read build state file {}", stateFile, e);
    }
    return null;
  }

  private static void store(BuildContextState state, File stateFile) throws IOException {
    ObjectOutputStream os =
        new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(stateFile)));
    try {
      os.writeObject(state);
    } finally {
      try {
        os.close();
      } catch (IOException e) {
        // ignore secondary exception
      }
    }
  }

  @Override
  public DefaultInput processInput(File file) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Iterable<DefaultInput> processInputs(FileSet fileSet) {
    // TODO Auto-generated method stub
    return null;
  }

  // low-level methods

  public Iterable<DefaultOutput> deleteStaleOutputs() {
    // TODO Auto-generated method stub
    return null;
  }

  /**
   * Returns {@code Input}s with this requirement
   */
  public Iterable<DefaultInput> getDependencies(String qualifier, Serializable localName) {
    Set<DefaultInput> result = requirementInputs.get(new QualifiedName(qualifier, localName));
    return result != null ? result : Collections.<DefaultInput>emptyList();
  }

  @Override
  public DefaultOutput registerOutput(File file) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DefaultOutput getOldOutput(File file) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public DefaultInput registerInput(File file) {
    if (!file.canRead()) {
      return null;
    }

    file = normalize(file);

    DefaultInput result = inputs.get(file);
    if (result == null) {
      // XXX do I do this right? need to check with the concurrency book
      inputs.putIfAbsent(file, new DefaultInput());
      result = inputs.get(file);
    }

    return result;
  }

  private File normalize(File file) {
    try {
      return file.getCanonicalFile();
    } catch (IOException e) {
      log.debug("Could not normalize file {}", file, e);
      return file.getAbsoluteFile();
    }
  }

  @Override
  public FileSetBuilder fileSetBuilder() {
    // TODO Auto-generated method stub
    return null;
  }
}
