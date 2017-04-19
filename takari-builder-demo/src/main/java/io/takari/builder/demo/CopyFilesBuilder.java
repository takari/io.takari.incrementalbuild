package io.takari.builder.demo;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import io.takari.builder.Builder;
import io.takari.builder.BuilderContext;
import io.takari.builder.IDirectoryFiles;
import io.takari.builder.InputDirectoryFiles;
import io.takari.builder.Messages;
import io.takari.builder.OutputDirectory;

/**
 * A simple builder that copies input files specified by {@code inputs} parameter to the specified
 * {@code to} output directory.
 * <p>
 * Inputs that are 13 bytes long are considered invalid and result in build failure.
 */
public class CopyFilesBuilder {
  
  private Messages messages = BuilderContext.getMessages();

  @InputDirectoryFiles(defaultValue = "${project.basedir}/src/files", defaultIncludes="**/*")
  private IDirectoryFiles inputs;

  @OutputDirectory(defaultValue = "${project.build.directory}/files")
  private File to;

  @Builder(name = "copy-files")
  public void copy() throws IOException {

    for (String inputName : inputs.filenames()) {
      File inputFile = new File(inputs.location(), inputName);

      // add error messages for invalid inputs and skip output generation
      if (inputFile.length() == 13) {
        messages.error(inputFile, 0 /* line */, 0 /* column */, "invalid file length", null);
        continue;
      }

      // generate the output
      Path outputFile = new File(to, inputName).toPath();
      Files.createDirectories(outputFile.getParent());
      try (OutputStream out = Files.newOutputStream(outputFile)) {
        Files.copy(inputFile.toPath(), out);
      }
    }

  }
}
