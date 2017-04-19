package io.takari.builder.internal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.plexus.util.xml.CompactXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import io.takari.builder.internal.digest.FileDigest;

class BuilderExecutionState {

  // inputs

  public final BuilderInputs.Digest inputsDigest;

  public final Map<String, Object> properties;

  public final Serializable classpathDigest;

  // outputs

  public final Collection<String> outputPaths;

  public final Set<CompileSourceRoot> compileSourceRoots;

  public final Set<ResourceRoot> resourceRoots;

  public final List<Message> messages;

  public final Map<String, FileDigest> exceptionsDigest;

  @SuppressWarnings("serial")
  private static class StateFormatException extends RuntimeException {
    public StateFormatException(Throwable cause) {
      super(cause);
    }
  }

  private BuilderExecutionState(BuilderInputs.Digest inputsDigest, Map<String, Object> properties,
      Serializable classpathDigest, Collection<String> outputPaths, Set<CompileSourceRoot> compileSourceRoots,
      Set<ResourceRoot> resourceRoots, List<Message> messages, Map<String, FileDigest> exceptionsDigest) {
    this.inputsDigest = inputsDigest;
    this.properties = properties;
    this.classpathDigest = classpathDigest;
    this.outputPaths = outputPaths;
    this.compileSourceRoots = compileSourceRoots;
    this.resourceRoots = resourceRoots;
    this.messages = messages;
    this.exceptionsDigest = exceptionsDigest;
  }

  @SuppressWarnings("unchecked")
  public static BuilderExecutionState load(Path file) {
    Collection<String> outputPaths = Collections.emptySet();
    if (file != null && Files.isRegularFile(file)) {
      try (ObjectInputStream is = newObjectInputStream(file)) {
        outputPaths = readOutputPaths(is);
        final BuilderInputs.Digest inputsDigest = (BuilderInputs.Digest)is.readObject();
        final Map<String, Object> properties = (Map<String, Object>)is.readObject();
        final Serializable classpathDigest = (Serializable)is.readObject();
        final Set<CompileSourceRoot> compileSourceRoots = (Set<CompileSourceRoot>)is.readObject();
        final Set<ResourceRoot> resourceRoots = (Set<ResourceRoot>)is.readObject();
        final List<Message> messages = (List<Message>)is.readObject();
        final Map<String, FileDigest> exceptionsDigest = (Map<String, FileDigest>)is.readObject();
        return new BuilderExecutionState(inputsDigest, properties, classpathDigest, outputPaths, compileSourceRoots,
            resourceRoots, messages, exceptionsDigest);
      } catch (IOException | ClassNotFoundException e) {}
    }
    return new BuilderExecutionState(BuilderInputs.emptyDigest(), Collections.emptyMap(), "", outputPaths,
        Collections.emptySet(), Collections.emptySet(), Collections.emptyList(), Collections.emptyMap());
  }

  private static ObjectInputStream newObjectInputStream(Path file) throws IOException {
    return new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)));
  }

  private static ObjectOutputStream newObjectOutputStream(Path file) throws IOException {
    return new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(file)));
  }

  public static void store(Path file, BuilderInputs.Digest digest, Map<String, Object> properties,
      Serializable classpathDigest, Collection<String> outputPaths, Set<CompileSourceRoot> compileSourceRoots,
      Set<ResourceRoot> resourceRoots, List<Message> messages, Map<String, FileDigest> exceptionsDigest)
      throws IOException {
    if (file == null) { return; }
    if (!Files.isDirectory(file.getParent())) {
      Files.createDirectories(file.getParent());
    }
    try (ObjectOutputStream os = newObjectOutputStream(file)) {
      writeOutputPaths(os, outputPaths);
      os.writeObject(digest);
      os.writeObject(properties);
      os.writeObject(classpathDigest);
      os.writeObject(compileSourceRoots);
      os.writeObject(resourceRoots);
      os.writeObject(messages);
      os.writeObject(exceptionsDigest);
    }
  }

  //
  // output paths are written (and read) before any other build state
  // the idea is to provide backwards/forward output path compatibility
  // without restrictions on changes of other parts of the build state
  // each path is encoded as <o>...</o> utf8 xml string. future versions
  // may introduce <strong>optional</string> xml attributes if needed
  //

  private static final String OUTPUT_DOM_NAME = "o";

  static void writeOutputPaths(ObjectOutputStream os, Collection<String> outputPaths) throws IOException {
    os.writeInt(outputPaths.size());
    for (String path : outputPaths) {
      os.writeUTF(toPortablePath(path));
    }
  }

  private static String toPortablePath(String path) {
    StringWriter buf = new StringWriter();
    CompactXMLWriter w = new CompactXMLWriter(buf);
    w.startElement(OUTPUT_DOM_NAME);
    w.writeText(path);
    w.endElement();
    return buf.toString();
  }

  static Collection<String> readOutputPaths(ObjectInputStream is) throws IOException {
    int size = is.readInt();
    Set<String> outputPaths = new HashSet<>(size);
    for (int i = 0; i < size; i++) {
      try {
        outputPaths.add(fromPortablePath(is.readUTF()));
      } catch (XmlPullParserException e) {
        throw new StateFormatException(e);
      }
    }
    return outputPaths;
  }

  private static String fromPortablePath(String string) throws XmlPullParserException, IOException {
    Xpp3Dom dom = Xpp3DomBuilder.build(new StringReader(string));
    if (!OUTPUT_DOM_NAME
        .equals(dom.getName())) { throw new XmlPullParserException("invalid element " + dom.getName()); }
    return dom.getValue();
  }

  /**
   * "Undo" log file writer. Each file record represents single output file created by the builder and can be used to
   * cleanup builder outputs if jvm crashes or is killed before builder state file is written.
   */
  static interface InprogressStateWriter extends Closeable {

    /**
     * Writes single "undo" log record. Not thread safe, access must be serialized externally. The undo record is
     * flushed to filesystem before this method exits.
     */
    public void writePath(String path) throws IncrementalBuildException;
  }

  static final InprogressStateWriter NOOP_INPROGRESSWRITER = new InprogressStateWriter() {
    @Override
    public void close() {}

    @Override
    public void writePath(String path) {}
  };

  static Collection<String> readInprogressOutputPaths(Path file) throws IOException {
    Set<String> paths = new HashSet<>();
    try (BufferedReader reader = Files.newBufferedReader(file)) {
      String str;
      while ((str = reader.readLine()) != null) {
        paths.add(fromPortablePath(str));
      }
    } catch (XmlPullParserException e) {
      // last log record can be corrupted if it was being written when jvm crashed
      // it is okay to ignore because corresponding output file could not have been created
    }
    return paths;
  }

  static InprogressStateWriter newInprogressWriter(Path file) throws IOException {
    Files.createDirectories(file.getParent());
    BufferedWriter writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.SYNC);
    return new InprogressStateWriter() {

      @Override
      public void close() throws IOException {
        writer.close();
      }

      @Override
      public void writePath(String path) throws IncrementalBuildException {
        try {
          writer.write(toPortablePath(path));
          writer.newLine();
          writer.flush();
        } catch (IOException e) {
          throw new IncrementalBuildException(e);
        }
      }
    };
  }
}
