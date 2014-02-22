package io.takari.incremental.demo;

import io.takari.incrementalbuild.BuildContext;

import java.io.File;

public class IncludedInputFilesMockup {
  private BuildContext context;

  public void generate(File inputFile, File includedFile) {

    BuildContext.Input<File> input = context.registerInput(inputFile).process();

    // creates association between input and included files
    // input file requires processing when any of its included files changes
    input.associateIncludedInput(context.registerInput(includedFile));

    // XXX deal will error/warning messages in included files
    // current thinking is to associate the messages with (input,includedInput) tuple
    // this will likely result in duplicate messages, but we can decide what it do about it later
  }
}
