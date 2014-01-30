package io.takari.incremental.demo;

import io.takari.incrementalbuild.BuildContext;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

public class ResourceFilteringMockup {

  // injected parameters

  BuildContext context;

  // end of injected parameters

  public void filter(Collection<File> fileSet) throws IOException {

    // all input files must be registered with BuildContext.
    // by tracking all input files incremental build library is able to determine "removed" inputs
    // and clean their associated outputs during subsequent builds
    for (BuildContext.Input<File> input : context.registerAndProcessInputs(fileSet)) {

      // input requires filtering if the input itself new/change or if the output is changed/deleted

      // input#getOldStatus can be used to determine input's up-to-date status compared to the
      // previous build

      File outputFile = null; // determine location of filtered output file somehow

      // register associated output or outputs. by tracking associated outputs, incremental build
      // library can cleanup stale outputs during subsequent builds.
      BuildContext.Output<File> output = input.associateOutput(outputFile);

      // Output#newOutputStream helps with Eclipse integration, but
      OutputStream os = output.newOutputStream();
      try {
        // actually do filtering
      } finally {
        os.close();
      }
    }

    // incremental build library performs automatic cleanup and error handling.
    // once BuildContext goes out of scope it deletes all stale/orphaned output files
    // it replays all messages not cleared since previous build
    // it triggers build failure if there any new or not cleared error messages
  }

}
