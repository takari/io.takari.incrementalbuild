package io.takari.incremental.demo;

import io.takari.incremental.BuildContext;
import io.takari.incremental.BuildContext.Input;
import io.takari.incremental.BuildContext.Output;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

// new aggregate output needs to be produced if there are new "interesting" inputs
// or when any of the inputs aggregated during previous build was changed or deleted
// if new aggregate is generated, all interesting inputs must be aggregated in the
// output regardless if they changed since last build or not.
public class InputAggregationMockup {

  private static final String KEY_INCREMENTAL_DATA = "mockup.data";

  /** @Injected */
  BuildContext context;

  public void aggregate(Collection<File> fileSet) throws IOException {

    // this is for demo purposes only, real code most likely will also collect per-input data
    Map<File, BuildContext.Input<File>> inputs =
        new LinkedHashMap<File, BuildContext.Input<File>>();

    // this iterates on any new/modified inputs from the fileset
    for (BuildContext.Input<File> input : context.processInputs(fileSet)) {
      File inputFile = input.getResource();

      // Not all new/changed inputs need to be aggregated. For example, for maven plugin.xml
      // only classes annotated with @Mojo need to be looked at. as indicated above, real
      // code will likely capture both interesting inputs and data to be aggregated
      if (isInteresting(inputFile)) {
        inputs.put(inputFile, input);

        // capture partial state in Input named property.
        // it will be persisted as part of BuildContext state and will be available during
        // subsequent builds
        input.setValue(KEY_INCREMENTAL_DATA, process(input));
      }
    }

    if (!inputs.isEmpty()) {
      File outputFile = getOutputFile();

      // collect old inputs that still part of fileSet and do not require processing
      // inputs that were deleted do not require processing (obviously)
      // inputs that require processing were processed above already
      Output<File> oldOutput = context.getOldOutput(outputFile);
      if (oldOutput != null) {
        for (BuildContext.Input<File> oldInput : oldOutput.getAssociatedInputs()) {
          File inputFile = oldInput.getResource();
          if (!inputs.containsKey(inputFile) && fileSet.contains(inputFile)
              && !oldInput.isProcessingRequired()) {
            // register input file with build context and copy the old value of the named property
            Input<File> input = context.registerInput(oldInput.getResource());
            input.setValue(KEY_INCREMENTAL_DATA,
                oldInput.getValue(KEY_INCREMENTAL_DATA, String.class));
            inputs.put(inputFile, input);
          }
        }
      }

      // register new "clean" output with the build context, then associate all relevant inputs
      BuildContext.Output<File> output = context.registerOutput(outputFile);
      OutputStream os = output.newOutputStream();
      try {
        for (BuildContext.Input<File> input : inputs.values()) {

          // inputs and outputs have symmetrical many-to-many relation
          output.associateInput(input);

          // append aggregator with data specific to the current input
          // then write everything to an xml/json/etc file
          contributeToAggregate(input, os);
        }
      } finally {
        os.close();
      }
    }

    // output will be deleted as orphaned if it does not have any associated inputs
    // TODO example that shows how to generate empty output even if all inputs are deleted
  }

  private Serializable process(Input<File> input) {
    // TODO Auto-generated method stub
    return null;
  }

  private void contributeToAggregate(Input<File> input, OutputStream os) {
    // TODO Auto-generated method stub

  }

  private File getOutputFile() {
    // TODO Auto-generated method stub
    return null;
  }

  private boolean isInteresting(File inputFile) {
    // TODO Auto-generated method stub
    return false;
  }
}
