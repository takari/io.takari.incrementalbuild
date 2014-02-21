package io.takari.incremental.demo;

import io.takari.incrementalbuild.BuildContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;

/**
 * Maven plugin uses one file from dependency artifact.
 */
public class DependencyResourceMockup {

  private static final String RESOURCE_PATH = "some/file";

  BuildContext context;

  Artifact dependency;

  public void execute() throws IOException {
    File file = dependency.getFile();

    if (file == null || !file.exists()) {
      // artifact was not resolved
      throw new IllegalStateException();
    }

    if (file.isFile()) {
      ZipFile zip = new ZipFile(file);
      try {
        ZipEntry resource = zip.getEntry(RESOURCE_PATH);
        InputStream is = zip.getInputStream(resource);
        try {
          doSomethingUsefulWithResource(is);
        } finally {
          is.close();
        }
      } finally {
        zip.close();
      }
    } else if (file.isDirectory()) {
      File resource = new File(file, RESOURCE_PATH);
      InputStream is = new FileInputStream(resource);
      try {
        doSomethingUsefulWithResource(is);
      } finally {
        is.close();
      }
    }
  }

  private void doSomethingUsefulWithResource(InputStream is) {}
}
