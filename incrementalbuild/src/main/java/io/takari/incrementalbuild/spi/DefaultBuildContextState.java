package io.takari.incrementalbuild.spi;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultBuildContextState implements Serializable {

  private static final long serialVersionUID = 6195150574931820441L;

  final Map<String, Serializable> configuration;

  final Map<File, ResourceHolder<File>> outputs = new HashMap<File, ResourceHolder<File>>();

  final Map<Object, ResourceHolder<?>> inputs = new HashMap<Object, ResourceHolder<?>>();

  final Map<Object, Collection<File>> inputOutputs = new HashMap<Object, Collection<File>>();

  final Map<File, Collection<Object>> outputInputs = new HashMap<File, Collection<Object>>();

  final Map<Object, Collection<Object>> inputIncludedInputs =
      new HashMap<Object, Collection<Object>>();

  final Map<QualifiedName, Collection<Object>> requirementInputs =
      new HashMap<QualifiedName, Collection<Object>>();

  final Map<Object, Collection<QualifiedName>> inputRequirements =
      new HashMap<Object, Collection<QualifiedName>>();

  final Map<File, Collection<QualifiedName>> outputCapabilities =
      new HashMap<File, Collection<QualifiedName>>();

  final Map<Object, Map<String, Serializable>> resourceAttributes =
      new HashMap<Object, Map<String, Serializable>>();

  final Map<Object, Collection<Message>> inputMessages = new HashMap<Object, Collection<Message>>();

  private DefaultBuildContextState(Map<String, Serializable> configuration) {
    this.configuration = configuration;
  }

  public static DefaultBuildContextState withConfiguration(Map<String, Serializable> configuration) {
    HashMap<String, Serializable> copy = new HashMap<String, Serializable>(configuration);
    // configuration marker used to distinguish between empty and new state
    copy.put("incremental", Boolean.TRUE);
    return new DefaultBuildContextState(Collections.unmodifiableMap(copy));
  }

  public static DefaultBuildContextState emptyState() {
    // TODO make state immutable
    return new DefaultBuildContextState(Collections.<String, Serializable>emptyMap());
  }

  public String getStats() {
    StringBuilder sb = new StringBuilder();

    sb.append(configuration.size()).append(' ');
    sb.append(inputs.size()).append(' ');
    sb.append(outputs.size()).append(' ');
    sb.append(inputOutputs.size()).append(' ');
    sb.append(outputInputs.size()).append(' ');
    sb.append(inputIncludedInputs.size()).append(' ');
    sb.append(requirementInputs.size()).append(' ');
    sb.append(inputRequirements.size()).append(' ');
    sb.append(outputCapabilities.size()).append(' ');
    sb.append(resourceAttributes.size()).append(' ');
    sb.append(inputMessages.size()).append(' ');

    return sb.toString();
  }

  public void storeTo(File stateFile) throws IOException {
    File parent = stateFile.getParentFile();
    if (!parent.isDirectory() && !parent.mkdirs()) {
      throw new IOException("Could not create directory " + parent);
    }

    ObjectOutputStream os =
        new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(stateFile)));
    try {
      os.writeObject(this);
    } finally {
      try {
        os.close();
      } catch (IOException e) {
        // ignore secondary exception
      }
    }
  }

  public static DefaultBuildContextState loadFrom(File stateFile) {
    // TODO verify stateFile location has not changed since last build
    // TODO wrap collections in corresponding immutable collections
    try {
      ObjectInputStream is =
          new ObjectInputStream(new BufferedInputStream(new FileInputStream(stateFile))) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException,
                ClassNotFoundException {
              // TODO does it matter if TCCL or super is called first?
              try {
                ClassLoader tccl = Thread.currentThread().getContextClassLoader();
                Class<?> clazz = tccl.loadClass(desc.getName());
                return clazz;
              } catch (ClassNotFoundException e) {
                return super.resolveClass(desc);
              }
            }
          };
      try {
        return (DefaultBuildContextState) is.readObject();
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
      throw new IllegalStateException("Could not read build state file " + stateFile, e);
    }
    return DefaultBuildContextState.emptyState();
  }

}
