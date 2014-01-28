package io.takari.incremental.demo;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.Output;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InputAggregationMockup {

  private static final String KEY_INCREMENTAL_DATA = "mockup.data";

  /** @Injected */
  BuildContext context;

  public void aggregate(Collection<File> fileSet) throws IOException {

    File outputFile = getOutputFile();

    // always recreate Output state
    BuildContext.Output<File> output = context.registerOutput(outputFile);

    Set<File> processed = new HashSet<File>();

    // this is the application data extracted from the inputs and written to the output
    List<Serializable> aggreagateData = new ArrayList<Serializable>();

    // indicates that new Output needs to be written
    boolean writeAggregateData = false;

    // this iterates over all new/modified inputs from the fileset
    for (BuildContext.Input<File> input : context.processInputs(fileSet)) {
      File inputFile = input.getResource();

      // extract the data from the input. this is application-specific logic.
      // note that not all inputs will have relevant data
      Serializable data = extractData(input.getResource());

      if (data != null) {
        // collect the data to be aggregated
        aggreagateData.add(data);

        // persist the data in Input attribute for use during future builds
        // as long as the input does not change, the attribute value will be used without the need
        // to extract data from the input again
        input.setValue(KEY_INCREMENTAL_DATA, data);

        // associate input and output. this will be used during future builds to determine deleted
        // outputs
        output.associateInput(input);
      }

      // mark inputFile as processed to make sure it is looked at only once
      processed.add(inputFile);
    }

    // process inputs associated with the output during the previous build
    Output<File> oldOutput = context.getOldOutput(outputFile);
    if (oldOutput != null) {
      for (BuildContext.Input<File> oldInput : oldOutput.getAssociatedInputs()) {
        File inputFile = oldInput.getResource();

        if (!processed.add(inputFile)) {
          // don't process the same changed inputFile twice
          continue;
        }

        if (!fileSet.contains(inputFile) || !inputFile.canRead()) {
          // the old input file was is not part of fileSet or was deleted
          // the aggregate must be regenerated (but without this input file)
          writeAggregateData = true;
        } else {
          // the input file has not changed since previous build
          // reuse the data extracted from the input during earlier build
          Serializable data = oldInput.getValue(KEY_INCREMENTAL_DATA, Serializable.class);
          aggreagateData.add(data);
          // carry over (old) input state as is and associate it with the (new) output
          Input<File> input = context.registerInput(inputFile);
          input.setValue(KEY_INCREMENTAL_DATA, data);
          output.associateInput(input);
        }
      }
    }

    if (writeAggregateData) {
      OutputStream os = output.newOutputStream();
      try {
        writeAggregate(os, aggreagateData);
      } finally {
        os.close();
      }
    }

  }

  private void writeAggregate(OutputStream os, List<Serializable> aggreagateData) {
    // TODO Auto-generated method stub

  }

  private Serializable extractData(File resource) {
    // TODO Auto-generated method stub
    return null;
  }

  private File getOutputFile() {
    // TODO Auto-generated method stub
    return null;
  }
}
