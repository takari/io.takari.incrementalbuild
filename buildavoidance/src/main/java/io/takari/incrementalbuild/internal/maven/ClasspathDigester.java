package io.takari.incrementalbuild.internal.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;

/**
 * Specialized digester for Maven plugin classpath dependencies. Uses class file contents and immune
 * to file timestamp changes caused by rebuilds of the same sources.
 */
public class ClasspathDigester {
  private final byte[] buf = new byte[4096];

  private final MessageDigest digester;

  public ClasspathDigester() {
    this.digester = SHA1Digester.newInstance();
  }

  public byte[] digest(List<Artifact> artifacts) throws IOException {
    digester.reset();
    for (Artifact artifact : artifacts) {
      File file = artifact.getFile();
      if (file.isFile()) {
        try {
          digestZipFile(file);
        } catch (ZipException e) {
          digestFile(file);
        }
      } else if (file.isDirectory()) {
        digestClassDirectory(file);
      } else {
        // file does not exist
      }
    }
    return digester.digest();
  }

  private void digestZipFile(File file) throws ZipException, IOException {
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
        digestAndCloseStream(zip.getInputStream(entry));
      }
    } finally {
      zip.close();
    }
  }

  private void digestAndCloseStream(InputStream inputStream) throws IOException {
    try {
      int len;
      while ((len = inputStream.read(buf)) > 0) {
        digester.update(buf, 0, len);
      }
    } finally {
      inputStream.close();
    }
  }

  private void digestClassDirectory(File directory) throws IOException {
    TreeSet<File> sorted = new TreeSet<File>();
    lsLR(directory, sorted);
    for (File file : sorted) {
      digestFile(file);
    }
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

  private void digestFile(File file) throws IOException {
    digestAndCloseStream(new FileInputStream(file));
  }
}
