package io.takari.builder.internal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
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
      Serializable classpathDigest, Collection<String> outputPaths,
      Set<CompileSourceRoot> compileSourceRoots, Set<ResourceRoot> resourceRoots,
      List<Message> messages, Map<String, FileDigest> exceptionsDigest) {
    this.inputsDigest = inputsDigest;
    this.properties = properties;
    this.classpathDigest = classpathDigest;
    this.outputPaths = outputPaths;
    this.compileSourceRoots = compileSourceRoots;
    this.resourceRoots = resourceRoots;
    this.messages = messages;
    this.exceptionsDigest = exceptionsDigest;
  }

  /**
   * Denotes whether the BuilderExecutionState is in an escalated state When it is in an escalated
   * state, the build needs to be run.
   * 
   * @return true if the BuilderExecutionState is in an escalated state false if it is not. By
   *         default, this will only return true. It must be extended to change the behavior.
   */
  public boolean isEscalated() {
    return false;
  }

  @SuppressWarnings("unchecked")
  public static BuilderExecutionState load(Path file) {
    Collection<String> outputPaths = Collections.emptySet();
    if (file != null && Files.isRegularFile(file)) {
      // outer try block prevents open file leak if wrapper stream constructors throws exceptions
      try (InputStream is = Files.newInputStream(file)) {
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(is))) {
          outputPaths = readOutputPaths(ois);
          final BuilderInputs.Digest inputsDigest = (BuilderInputs.Digest) ois.readObject();
          final Map<String, Object> properties = (Map<String, Object>) ois.readObject();
          final Serializable classpathDigest = (Serializable) ois.readObject();
          final Set<CompileSourceRoot> compileSourceRoots =
              (Set<CompileSourceRoot>) ois.readObject();
          final Set<ResourceRoot> resourceRoots = (Set<ResourceRoot>) ois.readObject();
          final List<Message> messages = (List<Message>) ois.readObject();
          final Map<String, FileDigest> exceptionsDigest =
              (Map<String, FileDigest>) ois.readObject();
          return new BuilderExecutionState(inputsDigest, properties, classpathDigest, outputPaths,
              compileSourceRoots, resourceRoots, messages, exceptionsDigest);
        }
      } catch (IOException | ClassNotFoundException e) {}
    }

    // If a normal BuilderExecutionState is not able to be built
    // Return an escalated state that states that there will need to be
    // a build for this particular builder. The escalated state will not hold
    // anything but the outputPaths of the builder
    return new EscalatedExecutionState(outputPaths);
  }

  public static void store(Path file, BuilderInputs.Digest digest, Map<String, Object> properties,
      Serializable classpathDigest, Collection<String> outputPaths,
      Set<CompileSourceRoot> compileSourceRoots, Set<ResourceRoot> resourceRoots,
      List<Message> messages, Map<String, FileDigest> exceptionsDigest) throws IOException {
    if (file == null) {
      return;
    }
    if (!Files.isDirectory(file.getParent())) {
      Files.createDirectories(file.getParent());
    }
    // outer try block prevents open file leak if wrapper stream constructors throws exceptions
    try (OutputStream os = Files.newOutputStream(file)) {
      try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(os))) {
        writeOutputPaths(oos, outputPaths);
        oos.writeObject(digest);
        oos.writeObject(properties);
        oos.writeObject(classpathDigest);
        oos.writeObject(compileSourceRoots);
        oos.writeObject(resourceRoots);
        oos.writeObject(messages);
        oos.writeObject(exceptionsDigest);
      }
    }
  }

  private static class EscalatedExecutionState extends BuilderExecutionState {
    EscalatedExecutionState(Collection<String> outputPaths) {
      super(BuilderInputs.emptyDigest(), Collections.emptyMap(), "", outputPaths,
          Collections.emptySet(), Collections.emptySet(), Collections.emptyList(),
          Collections.emptyMap());
    }

    @Override
    public boolean isEscalated() {
      return true;
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

  static void writeOutputPaths(ObjectOutputStream os, Collection<String> outputPaths)
      throws IOException {
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
    if (!OUTPUT_DOM_NAME.equals(dom.getName())) {
      throw new XmlPullParserException("invalid element " + dom.getName());
    }
    return dom.getValue();
  }

  /**
   * "Undo" log file writer. Each file record represents single output file created by the builder
   * and can be used to cleanup builder outputs if jvm crashes or is killed before builder state
   * file is written.
   */
  static interface InprogressStateWriter extends Closeable {

    /**
     * Writes single "undo" log record. Not thread safe, access must be serialized externally. The
     * undo record is flushed to filesystem before this method exits.
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
    BufferedWriter writer =
        Files.newBufferedWriter(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.SYNC);
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
