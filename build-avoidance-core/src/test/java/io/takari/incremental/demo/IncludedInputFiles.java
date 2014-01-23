package io.takari.incremental.demo;

import io.takari.incremental.BuildContext;

import java.io.File;

public class IncludedInputFiles {
  private BuildContext context;

  public void generate(File inputFile, File includedFile) {

    BuildContext.Input input = context.registerInput(inputFile);

    // creates association between input and included files
    // input file requires processing when any of its included files changes
    input.registerIncludedFile(includedFile);

    // XXX deal will error/warning messages in included files
    // current thinking is to associate the messages with (input,includedInput) tuple
    // this will likely result in duplicate messages, but we can decide what it do about it later
  }
}
