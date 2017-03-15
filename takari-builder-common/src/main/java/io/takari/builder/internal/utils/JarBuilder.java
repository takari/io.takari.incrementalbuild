package io.takari.builder.internal.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class JarBuilder {
  public static final Charset UTF8 = Charset.forName("UTF-8");

  final File file;
  final JarOutputStream out;

  private JarBuilder(File jar) throws IOException {
    this.file = jar;
    this.out = new JarOutputStream(new FileOutputStream(jar));
  }

  public JarBuilder withEntries(String... paths) throws IOException {
    for (String path : paths) {
      writeFileEntry(path, null);
    }

    return this;
  }

  public JarBuilder withEntry(String path, String content) throws IOException {
    writeFileEntry(path, content);

    return this;
  }

  private void writeFileEntry(String path, String content) throws IOException {
    if (path.startsWith("/") || path.endsWith("/")) {
      throw new IllegalArgumentException("jar entry path must not start or end with '/'.");
    }

    out.putNextEntry(new ZipEntry(path));
    if (content != null) {
      out.write(content.getBytes(UTF8));
    }
    out.closeEntry();
  }

  public File build() throws IOException {
    out.close();
    return file;
  }

  public static JarBuilder create(File file) throws IOException {
    return new JarBuilder(file);
  }
}
