package io.takari.incremental.demo;

import io.takari.incremental.BuildContext;
import io.takari.incremental.FileSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

public class ResourceFilteringMockup {

  // injected parameters

  BuildContext context;

  File sourceDirectory;

  Set<String> includes;

  Set<String> excludes;

  File outputDirectory;

  // end of injected parameters

  public void filter() throws IOException {

    FileSet fileSet = context.fileSetBuilder().withBasedir(sourceDirectory) //
        .addIncludes(excludes) //
        .addExcludes(excludes) //
        .build();

    // all input files must be registered with BuildContext
    // by tracking all input files build-avoidance API is able to determine
    // what input files will require processing during the next build and
    // cleanup output files that are no longer necessary
    for (BuildContext.Input<File> input : context.processInputs(fileSet)) {

      // BuildContext records input file size and lastModified timestamp (and sha1?)
      // input file requires processing if it's changed since last build
      // at this point build-avoidance tracks two input instances, previous and current
      // the previous instance tracks outputs and messages associated with the input during last
      // build. the current instance has not associated outputs nor messages.

      // mapping input->output files is outside of the scope of build-avoidance API
      File outputFile = mapOutputFile(sourceDirectory, input.getResource(), outputDirectory);

      // all output files and their relationship to input files must be registered with
      // BuildAvoidance framework
      BuildContext.Output<File> output = input.associateOutput(outputFile);

      // BuildContext records output file size and lastModified timestamp (and sha1?)
      // input file requires processing if it's associated output file(s) change
      try (OutputStream os = output.newOutputStream()) {

        // output file generation is obviously outside of the scope of build-avoidance API
        filter(input.getResource(), os);
      }

      // use of Output#newOutputStream is optional but recommended
      // the following snippet has the same effect the code as above
      // use of Output#newOutputStream does help with Eclipse integration
      File outputFile2 = mapOutputFile2(sourceDirectory, input.getResource(), outputDirectory);
      try (OutputStream os = new FileOutputStream(outputFile2)) {
        filter(input.getResource(), os);
      }
      input.associateOutput(outputFile);

    }

    // build-avoidance performs automatic cleanup and error handling
    // once BuildContext goes out of scope it deletes all stale/orphaned output files
    // it replays all messages not cleared since previous build
    // it triggers build failure if there any new or not cleared error messages
  }

  private void filter(File inputFile, OutputStream os) {
    // TODO Auto-generated method stub
  }

  private File mapOutputFile(File sourceDirectory, File sourceFile, File outputDirectory) {
    // TODO Auto-generated method stub
    return null;
  }

  private File mapOutputFile2(File sourceDirectory, File sourceFile, File outputDirectory) {
    // TODO Auto-generated method stub
    return null;
  }

}
