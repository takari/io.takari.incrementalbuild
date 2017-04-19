package io.takari.incrementalbuild.maven.internal.digest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.SessionData;

import io.takari.builder.internal.digest.BytesHash;
import io.takari.builder.internal.digest.SHA1Digester;


/**
 * Specialized digester for Maven plugin classpath dependencies. Uses class file contents and immune
 * to file timestamp changes caused by rebuilds of the same sources.
 */
class ClasspathDigester {

  private static final String SESSION_DATA_KEY = ClasspathDigester.class.getName();

  private final ConcurrentMap<String, byte[]> cache;

  @Inject
  public ClasspathDigester(MavenSession session) {
    this.cache = getCache(session);
  }

  /** for testing purposes */
  ClasspathDigester(ConcurrentMap<String, byte[]> cache) {
    this.cache = cache;
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentMap<String, byte[]> getCache(MavenSession session) {
    // this assumes that Aether repository session data does not change during reactor build
    SessionData sessionData = session.getRepositorySession().getData();
    if (sessionData.get(SESSION_DATA_KEY) == null) {
      sessionData.set(SESSION_DATA_KEY, null, new ConcurrentHashMap<String, byte[]>());
    }
    return (ConcurrentMap<String, byte[]>) sessionData.get(SESSION_DATA_KEY);
  }

  private static class JarDigester implements Callable<byte[]> {

    private final File file;

    public JarDigester(File file) {
      this.file = file;
    }

    @Override
    public byte[] call() throws IOException {
      MessageDigest digester = SHA1Digester.newInstance();
      try {
        digestZip(digester, file);
      } catch (ZipException e) {
        digestFile(digester, file);
      }
      return digester.digest();
    }

  }

  private static class ClassDirectoryDigester implements Callable<byte[]> {

    private final File directory;

    public ClassDirectoryDigester(File directory) {
      this.directory = directory;
    }

    @Override
    public byte[] call() throws IOException {
      MessageDigest digester = SHA1Digester.newInstance();

      TreeSet<File> sorted = new TreeSet<File>();
      lsLR(directory, sorted);
      for (File file : sorted) {
        digestFile(digester, file);
      }

      return digester.digest();
    }

    private void lsLR(File directory, TreeSet<File> sorted) {
      File[] files = directory.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.isDirectory()) {
            lsLR(file, sorted);
          } else {
            sorted.add(file);
          }
        }
      }
    }
  }

  public Serializable digest(List<Artifact> artifacts) throws IOException {
    MessageDigest digester = SHA1Digester.newInstance();
    for (Artifact artifact : artifacts) {
      File file = artifact.getFile();
      String cacheKey = getArtifactKey(artifact);
      byte[] cached = cache.get(cacheKey);
      if (cached == null) {
        byte[] hash;
        if (file.isFile()) {
          hash = new JarDigester(file).call();
        } else if (file.isDirectory()) {
          hash = new ClassDirectoryDigester(file).call();
        } else {
          // does not exist, use token empty array to avoid rechecking
          hash = new byte[0];
        }
        cached = cache.putIfAbsent(cacheKey, hash);
        if (cached == null) {
          cached = hash;
        }
      }
      digester.update(cached);
    }
    return new BytesHash(digester.digest());
  }

  private String getArtifactKey(Artifact artifact) {
    StringBuilder sb = new StringBuilder();
    sb.append(artifact.getGroupId());
    sb.append(':');
    sb.append(artifact.getArtifactId());
    sb.append(':');
    sb.append(artifact.getVersion());
    if (artifact.getClassifier() != null) {
      sb.append(':');
      sb.append(artifact.getClassifier());
    }
    return sb.toString();
  }

  static void digest(MessageDigest digester, InputStream is) throws IOException {
    byte[] buf = new byte[4096];
    int r;
    while ((r = is.read(buf)) > 0) {
      digester.update(buf, 0, r);
    }
  }

  static void digestFile(MessageDigest digester, File file) throws IOException {
    FileInputStream is = new FileInputStream(file);
    try {
      digest(digester, is);
    } finally {
      is.close();
    }
  }

  static void digestZip(MessageDigest digester, File file) throws IOException {
    ZipFile zip = new ZipFile(file);
    try {
      // sort entries.
      // order of jar/zip entries is not important but may change from one build to the next
      TreeSet<ZipEntry> sorted = new TreeSet<ZipEntry>(new Comparator<ZipEntry>() {
        @Override
        public int compare(ZipEntry o1, ZipEntry o2) {
          return o2.getName().compareTo(o1.getName());
        }
      });
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        sorted.add(entries.nextElement());
      }
      for (ZipEntry entry : sorted) {
        InputStream is = zip.getInputStream(entry);
        try {
          digest(digester, is);
        } finally {
          is.close();
        }
      }
    } finally {
      zip.close();
    }
  }
}
