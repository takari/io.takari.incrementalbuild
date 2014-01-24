package io.takari.incremental.demo;

import io.takari.incremental.BuildContext;

import java.io.File;

public class IncludedInputFilesMockup {
  private BuildContext context;

  public void generate(File inputFile, File includedFile) {

    BuildContext.Input<File> input = context.processInput(inputFile);

    if (input != null) {
      // creates association between input and included files
      // input file requires processing when any of its included files changes
      input.associateIncludedInput(includedFile);
    }

    // XXX deal will error/warning messages in included files
    // current thinking is to associate the messages with (input,includedInput) tuple
    // this will likely result in duplicate messages, but we can decide what it do about it later
  }
}
