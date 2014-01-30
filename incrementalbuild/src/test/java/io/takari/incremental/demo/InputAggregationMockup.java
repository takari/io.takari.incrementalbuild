package io.takari.incremental.demo;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class InputAggregationMockup {

  private static final String KEY_INCREMENTAL_DATA = "mockup.data";

  /** @Injected */
  BuildContext context;

  public void aggregate(Collection<File> fileSet) throws IOException {
    File outputFile = null; // determine location of the output file somehow

    // processed outputs are never deleted by the incremental build library
    // return Output object provides access to old state
    BuildContext.Output<File> output = context.processOutput(outputFile);

    // this is the application data extracted from the inputs and written to the output
    List<Serializable> aggreagateData = new ArrayList<Serializable>();

    // indicates that new Output needs to be written
    boolean writeAggregateData = false;

    // Output needs to be regenerated if any of the "old" inputs was removed
    BuildContext.OutputMetadata<File> oldOutput = output.getOldMetadata();
    if (oldOutput != null) {
      for (BuildContext.Input<File> input : output.getAssociatedInputs()) {
        File inputFile = input.getResource();
        if (!inputFile.canRead() || !fileSet.contains(inputFile)) {

          // indicate that the output needs to be regenerate
          writeAggregateData = true;
          break;
        }
      }
    }

    // all input files must be registered with BuildContext.
    for (BuildContext.InputMetadata<File> inputMetadata : context.registerInputs(fileSet)) {

      Serializable data = null;
      
      if (inputMetadata.getStatus() == ResourceStatus.UNMODIFIED) {
        // the input file has not changed since previous build
        // reuse the data extracted from the input during earlier build
        data = inputMetadata.getValue(KEY_INCREMENTAL_DATA, Serializable.class);

        // Note that this does not trigger output regeneration but the data will be included
        // if regeneration is needed because any other input was added, modified or deleted

        // Note that this leaves the input registered but not processed, which means all input's
        // metadata will be carried over
      } else {
        // notify incremental build library that input is being processed.
        // this clears any input state carried over from the previous build
        // in particular, it makes all outputs associated with the input eligible for cleanup and
        // clears all messages associated with the input during the previous build
        BuildContext.Input<File> input = inputMetadata.process();

        data = extractDataSomehow(input); // this is application specific logic

        if (data != null) {
          // persist the extracted data in Input attribute for use during future builds
          // as long as the input does not change, the attribute value will be used without the need
          // to extract data from the input again
          input.setValue(KEY_INCREMENTAL_DATA, data);

          // indicate that the output needs to be regenerate
          writeAggregateData = true;
        }
      }

      if (data != null) {
        aggreagateData.add(data); // collect the data to be aggregated

        // associate input and output. this will be used during future builds to determine deleted
        // outputs
        output.associateInput(inputMetadata);
      }
    }

    if (writeAggregateData) {
      OutputStream os = output.newOutputStream();
      try {
        //
      } finally {
        os.close();
      }
    }
  }

  private Serializable extractDataSomehow(Input<File> input) {
    // TODO Auto-generated method stub
    return null;
  }
}
