package io.takari.incremental.demo;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Input;

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


    // process all new/modified inputs and capture extracted data in BuildContext
    for (BuildContext.Input<File> input : context.registerAndProcessInputs(fileSet)) {
      Serializable data = extractDataSomehow(input); // this is application specific logic
      if (data != null) {
        // persist the extracted data in Input attribute for use during future builds
        // as long as the input does not change, the attribute value will be used without the need
        // to extract data from the input again
        input.setValue(KEY_INCREMENTAL_DATA, data);
      }
    }

    // go over all inputs registered during this and previous builds
    // collect extracted data and determine if output needs to be regenerated
    // for consistency with one-to-many scenario, associate inputs with outputs

    File outputFile = null; // determine location of the output file somehow

    // processed outputs are never deleted by the incremental build library
    // return Output object provides access to old state
    BuildContext.Output<File> output = context.processOutput(outputFile);

    // indicates that new Output needs to be written
    boolean writeAggregateData = false;

    // this is the application data extracted from the inputs
    List<Serializable> aggreagateData = new ArrayList<Serializable>();

    for (BuildContext.InputMetadata<File> input : context.getRegisteredInputs()) {
      Serializable data = input.getValue(KEY_INCREMENTAL_DATA, Serializable.class);
      if (data != null) {
        switch (input.getStatus()) {
          case NEW:
          case MODIFIED:
            // regenerate output if there were new/modified inputs during this build
            writeAggregateData = true;
            // use data extracted from new/modified inputs during this build
            aggreagateData.add(data);
            break;
          case UNMODIFIED:
            // use data extracted from unmodified inputs during previous builds
            // note that unmodified inputs do not trigger output regeneration
            aggreagateData.add(data);
            break;
          case REMOVED:
            // regenerate output if there were removed inputs since previous build
            writeAggregateData = true;
            break;
        }
      }
    }

    // generate output if there were relevant changes since previous build
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
