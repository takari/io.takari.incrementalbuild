package io.takari.incrementalbuild.spi;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultBuildContextState implements Serializable {

  private static final transient Logger log = LoggerFactory
      .getLogger(DefaultBuildContextState.class);

  private static final long serialVersionUID = 6195150574931820441L;

  final Map<String, Serializable> configuration;

  final Map<File, ResourceHolder<File>> outputs;

  final Map<Object, ResourceHolder<?>> inputs;

  final Map<Object, ResourceHolder<?>> includedInputs;

  final Map<Object, Collection<File>> inputOutputs;

  final Map<File, Collection<Object>> outputInputs;

  final Map<Object, Collection<Object>> inputIncludedInputs;

  final Map<QualifiedName, Collection<Object>> requirementInputs;

  final Map<Object, Collection<QualifiedName>> inputRequirements;

  final Map<File, Collection<QualifiedName>> outputCapabilities;

  final Map<Object, Map<String, Serializable>> resourceAttributes;

  final Map<Object, Collection<Message>> resourceMessages;

  private DefaultBuildContextState(Map<String, Serializable> configuration //
      , Map<Object, ResourceHolder<?>> inputs //
      , Map<File, ResourceHolder<File>> outputs //
      , Map<Object, ResourceHolder<?>> includedInputs //
      , Map<Object, Collection<File>> inputOutputs //
      , Map<File, Collection<Object>> outputInputs //
      , Map<Object, Collection<Object>> inputIncludedInputs //
      , Map<QualifiedName, Collection<Object>> requirementInputs //
      , Map<Object, Collection<QualifiedName>> inputRequirements //
      , Map<File, Collection<QualifiedName>> outputCapabilities //
      , Map<Object, Map<String, Serializable>> resourceAttributes //
      , Map<Object, Collection<Message>> messages) {
    this.configuration = configuration;
    this.inputs = inputs;
    this.outputs = outputs;
    this.includedInputs = includedInputs;
    this.inputOutputs = inputOutputs;
    this.outputInputs = outputInputs;
    this.inputIncludedInputs = inputIncludedInputs;
    this.requirementInputs = requirementInputs;
    this.inputRequirements = inputRequirements;
    this.outputCapabilities = outputCapabilities;
    this.resourceAttributes = resourceAttributes;
    this.resourceMessages = messages;
  }

  public static DefaultBuildContextState withConfiguration(Map<String, Serializable> configuration) {
    HashMap<String, Serializable> copy = new HashMap<String, Serializable>(configuration);
    // configuration marker used to distinguish between empty and new state
    copy.put("incremental", Boolean.TRUE);
    return new DefaultBuildContextState(Collections.<String, Serializable>unmodifiableMap(copy) // configuration
        , new HashMap<Object, ResourceHolder<?>>() // inputs
        , new HashMap<File, ResourceHolder<File>>() // outputs
        , new HashMap<Object, ResourceHolder<?>>() // includedInputs
        , new HashMap<Object, Collection<File>>() // inputOutputs
        , new HashMap<File, Collection<Object>>() // outputInputs
        , new HashMap<Object, Collection<Object>>() // inputIncludedInputs
        , new HashMap<QualifiedName, Collection<Object>>() // requirementInputs
        , new HashMap<Object, Collection<QualifiedName>>() // inputRequirements
        , new HashMap<File, Collection<QualifiedName>>() // outputCapabilities
        , new HashMap<Object, Map<String, Serializable>>() // resourceAttributes
        , new HashMap<Object, Collection<Message>>() // messages
    );
  }

  public static DefaultBuildContextState emptyState() {
    return new DefaultBuildContextState(Collections.<String, Serializable>emptyMap() // configuration
        , Collections.<Object, ResourceHolder<?>>emptyMap() // inputs //
        , Collections.<File, ResourceHolder<File>>emptyMap() // outputs //
        , Collections.<Object, ResourceHolder<?>>emptyMap() // includedInputs //
        , Collections.<Object, Collection<File>>emptyMap() // inputOutputs //
        , Collections.<File, Collection<Object>>emptyMap() // outputInputs //
        , Collections.<Object, Collection<Object>>emptyMap() // inputIncludedInputs //
        , Collections.<QualifiedName, Collection<Object>>emptyMap() // requirementInputs //
        , Collections.<Object, Collection<QualifiedName>>emptyMap() // inputRequirements //
        , Collections.<File, Collection<QualifiedName>>emptyMap() // outputCapabilities //
        , Collections.<Object, Map<String, Serializable>>emptyMap() // resourceAttributes //
        , Collections.<Object, Collection<Message>>emptyMap() // messages
    );
  }

  public String getStats() {
    StringBuilder sb = new StringBuilder();

    sb.append(configuration.size()).append(' ');
    sb.append(inputs.size()).append(' ');
    sb.append(includedInputs.size()).append(' ');
    sb.append(outputs.size()).append(' ');
    sb.append(inputOutputs.size()).append(' ');
    sb.append(outputInputs.size()).append(' ');
    sb.append(inputIncludedInputs.size()).append(' ');
    sb.append(requirementInputs.size()).append(' ');
    sb.append(inputRequirements.size()).append(' ');
    sb.append(outputCapabilities.size()).append(' ');
    sb.append(resourceAttributes.size()).append(' ');
    sb.append(resourceMessages.size()).append(' ');

    return sb.toString();
  }

  public void storeTo(OutputStream os) throws IOException {
    ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(os));
    try {
      writeMap(oos, this.configuration);
      writeMap(oos, this.outputs);
      writeMap(oos, this.inputs);
      writeMap(oos, this.includedInputs);

      writeMultimap(oos, inputOutputs);
      writeMultimap(oos, inputIncludedInputs);
      writeDoublemap(oos, resourceAttributes);
      writeMultimap(oos, resourceMessages);

      writeCapabilityConsumers(oos, requirementInputs);
      writeCapabilityProviders(oos, outputCapabilities);
    } finally {
      oos.flush();
    }
  }

  private void writeCapabilityProviders(ObjectOutputStream oos,
      Map<File, Collection<QualifiedName>> providers) throws IOException {
    oos.writeInt(providers.size());
    for (Map.Entry<File, Collection<QualifiedName>> entry : providers.entrySet()) {
      oos.writeObject(entry.getKey());
      Collection<QualifiedName> qnames = entry.getValue();
      oos.writeInt(qnames.size());
      for (QualifiedName qname : qnames) {
        writeQualifiedName(oos, qname);
      }
    }
  }

  private void writeCapabilityConsumers(ObjectOutputStream oos,
      Map<QualifiedName, Collection<Object>> consumers) throws IOException {
    oos.writeInt(consumers.size());
    for (Map.Entry<QualifiedName, Collection<Object>> entry : consumers.entrySet()) {
      writeQualifiedName(oos, entry.getKey());
      writeCollection(oos, entry.getValue());
    }
  }

  private void writeQualifiedName(ObjectOutputStream oos, QualifiedName qname) throws IOException {
    oos.writeObject(qname.getQualifier());
    oos.writeObject(qname.getLocalName());
  }

  private static void writeMap(ObjectOutputStream oos, Map<?, ?> map) throws IOException {
    oos.writeInt(map.size());
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      oos.writeObject(entry.getKey());
      oos.writeObject(entry.getValue());
    }
  }

  private static void writeMultimap(ObjectOutputStream oos, Map<?, ? extends Collection<?>> mmap)
      throws IOException {
    oos.writeInt(mmap.size());
    for (Map.Entry<?, ? extends Collection<?>> entry : mmap.entrySet()) {
      oos.writeObject(entry.getKey());
      writeCollection(oos, entry.getValue());
    }
  }

  private static void writeCollection(ObjectOutputStream oos, Collection<?> collection)
      throws IOException {
    if (collection == null || collection.isEmpty()) {
      oos.writeInt(0);
    } else {
      oos.writeInt(collection.size());
      for (Object element : collection) {
        oos.writeObject(element);
      }
    }
  }

  private static void writeDoublemap(ObjectOutputStream oos, Map<?, ? extends Map<?, ?>> dmap)
      throws IOException {
    oos.writeInt(dmap.size());
    for (Map.Entry<?, ? extends Map<?, ?>> entry : dmap.entrySet()) {
      oos.writeObject(entry.getKey());
      writeMap(oos, entry.getValue());
    }
  }

  public static DefaultBuildContextState loadFrom(File stateFile) {
    // TODO verify stateFile location has not changed since last build
    // TODO wrap collections in corresponding immutable collections

    if (stateFile == null) {
      // transient build context
      return DefaultBuildContextState.emptyState();
    }

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
        final long start = System.currentTimeMillis();

        Map<String, Serializable> configuration = readMap(is);
        Map<File, ResourceHolder<File>> outputs = readMap(is);
        Map<Object, ResourceHolder<?>> inputs = readMap(is);
        Map<Object, ResourceHolder<?>> includedInputs = readMap(is);

        Map<Object, Collection<File>> inputOutputs = readMultimap(is);
        Map<File, Collection<Object>> outputInputs = invertMultimap(inputOutputs);
        Map<Object, Collection<Object>> inputIncludedInputs = readMultimap(is);
        Map<Object, Map<String, Serializable>> resourceAttributes = readDoublemap(is);
        Map<Object, Collection<Message>> messages = readMultimap(is);

        Map<QualifiedName, Collection<Object>> requirementInputs = readCapabilityConsumers(is);
        Map<Object, Collection<QualifiedName>> inputRequirements =
            invertMultimap(requirementInputs);
        Map<File, Collection<QualifiedName>> outputCapabilities = readCapabilityProviders(is);

        DefaultBuildContextState state = new DefaultBuildContextState(configuration //
            , inputs //
            , outputs //
            , includedInputs //
            , inputOutputs //
            , outputInputs //
            , inputIncludedInputs //
            , requirementInputs //
            , inputRequirements //
            , outputCapabilities //
            , resourceAttributes //
            , messages //
            );
        log.debug("Loaded incremental build state {} ({} ms)", stateFile,
            System.currentTimeMillis() - start);
        return state;
      } finally {
        try {
          is.close();
        } catch (IOException e) {
          // ignore secondary exceptions
        }
      }
    } catch (FileNotFoundException e) {
      // this is expected, silently ignore
    } catch (RuntimeException e) {
      // this is a bug in our code, let it bubble up as build failure
      throw e;
    } catch (Exception e) {
      // this is almost certainly caused by incompatible state file, log and continue
      log.debug("Could not load incremental build state {}", stateFile, e);
    }
    return DefaultBuildContextState.emptyState();
  }


  private static Map<File, Collection<QualifiedName>> readCapabilityProviders(ObjectInputStream ois)
      throws ClassNotFoundException, IOException {
    Map<File, Collection<QualifiedName>> providers = new HashMap<File, Collection<QualifiedName>>();
    int size = ois.readInt();
    for (int i = 0; i < size; i++) {
      File key = (File) ois.readObject();
      int vsize = ois.readInt();
      Collection<QualifiedName> value = new ArrayList<QualifiedName>();
      for (int j = 0; j < vsize; j++) {
        value.add(readQualifiedName(ois));
      }
      providers.put(key, value);
    }
    return providers;
  }

  private static Map<QualifiedName, Collection<Object>> readCapabilityConsumers(
      ObjectInputStream ois) throws IOException, ClassNotFoundException {
    Map<QualifiedName, Collection<Object>> result =
        new HashMap<QualifiedName, Collection<Object>>();
    int size = ois.readInt();
    for (int i = 0; i < size; i++) {
      QualifiedName qname = readQualifiedName(ois);
      Collection<Object> consumers = readCollection(ois);
      result.put(qname, consumers);
    }
    return result;
  }

  private static QualifiedName readQualifiedName(ObjectInputStream ois)
      throws ClassNotFoundException, IOException {
    String qualified = (String) ois.readObject();
    String name = (String) ois.readObject();
    return new QualifiedName(qualified, name);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Map<K, V> readMap(ObjectInputStream ois) throws IOException,
      ClassNotFoundException {
    Map<K, V> map = new HashMap<K, V>();
    int size = ois.readInt();
    for (int i = 0; i < size; i++) {
      K key = (K) ois.readObject();
      V value = (V) ois.readObject();
      map.put(key, value);
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private static <K, V> Map<K, Collection<V>> readMultimap(ObjectInputStream ois)
      throws IOException, ClassNotFoundException {
    Map<K, Collection<V>> mmap = new HashMap<K, Collection<V>>();
    int size = ois.readInt();
    for (int i = 0; i < size; i++) {
      K key = (K) ois.readObject();
      Collection<V> value = readCollection(ois);
      mmap.put(key, value);
    }
    return mmap;
  }

  @SuppressWarnings("unchecked")
  private static <V> Collection<V> readCollection(ObjectInputStream ois) throws IOException,
      ClassNotFoundException {
    int size = ois.readInt();
    if (size == 0) {
      return null;
    }
    Collection<V> collection = new ArrayList<V>();
    for (int i = 0; i < size; i++) {
      collection.add((V) ois.readObject());
    }
    return collection;
  }

  @SuppressWarnings("unchecked")
  private static <K, VK, VV> Map<K, Map<VK, VV>> readDoublemap(ObjectInputStream ois)
      throws IOException, ClassNotFoundException {
    int size = ois.readInt();
    Map<K, Map<VK, VV>> dmap = new HashMap<K, Map<VK, VV>>();
    for (int i = 0; i < size; i++) {
      K key = (K) ois.readObject();
      Map<VK, VV> value = readMap(ois);
      dmap.put(key, value);
    }
    return dmap;
  }

  private static <K, V> Map<V, Collection<K>> invertMultimap(Map<K, Collection<V>> mmap) {
    Map<V, Collection<K>> inverted = new HashMap<V, Collection<K>>();
    for (Map.Entry<K, Collection<V>> entry : mmap.entrySet()) {
      for (V value : entry.getValue()) {
        Collection<K> keys = inverted.get(value);
        if (keys == null) {
          keys = new ArrayList<K>();
          inverted.put(value, keys);
        }
        keys.add(entry.getKey());
      }
    }
    return inverted;
  }
}
