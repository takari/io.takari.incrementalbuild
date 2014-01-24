package io.takari.incremental.demo;

import io.takari.incremental.BuildContext;
import io.takari.incremental.BuildContext.Input;
import io.takari.incremental.FileSet;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

// new aggregate output needs to be produced if there are new "interesting" inputs
// or when any of the inputs aggregated during previous build was changed or deleted
// if new aggregate is generated, all interesting inputs must be aggregated in the
// output regardless if they changed since last build or not.
public class InputAggregationMockup {

  /** @Injected */
  BuildContext context;

  public void aggregate(FileSet fileSet) throws IOException {

    // this is for demo purposes only, real code most likely will also collect per-input data
    Set<BuildContext.Input> inputs = new LinkedHashSet<>();

    boolean processingRequired = false;

    // this iterates on any new/modified inputs from the fileset
    for (BuildContext.Input input : context.registerInputsForProcessing(fileSet)) {

      // Not all new/changed inputs need to be aggregated. For example, for maven plugin.xml
      // only classes annotated with @Mojo need to be looked at. as indicated above, real
      // code will likely capture both interesting inputs and data to be aggregated
      if (isInteresting(input)) {

        //
        input.setValue("aggregate.data", process(input));

        inputs.add(input);
        processingRequired = true;
      }
    }

    File outputFile = getOutputFile();

    // oldOutput is read-only Output instance that provide information about end result of the
    // previous build.
    BuildContext.Output oldOutput = context.getOldOutput(outputFile);
    if (oldOutput != null) {
      if (!processingRequired) {
        // iterate over all Inputs that contributed to the aggregator during previous build
        // processing is required if any of old inputs was changed or removed since last build
        for (BuildContext.Input oldInput : oldOutput.getRegisteredInputs()) {
          if (oldInput.isProcessingRequired() || !fileSet.contains(oldInput.getResource())) {
            processingRequired = true;
          }
        }
      }
      if (processingRequired) {
        // if processing is required, aggregate all oldInputs that still exist and are still path of
        // the input FileSet. All changed inputs are processed at this point, so only old/unchanged
        // inputs need to be copied over
        for (BuildContext.Input oldInput : oldOutput.getRegisteredInputs()) {
          if (oldInput.getResource().canRead() && fileSet.contains(oldInput.getResource())) {
            Input input = context.registerInput(oldInput.getResource());
            input.setValue("aggregate.data", oldInput.getValue("aggregate.data", String.class));
            inputs.add(input);
          }
        }
      }
    }

    if (processingRequired) {

      // registers new "clean" output with the build context, then associate all relevant inputs
      BuildContext.Output output = context.registerOutput(outputFile);
      try (OutputStream os = output.newOutputStream()) {
        for (BuildContext.Input input : inputs) {

          // inputs and outputs have symmetrical many-to-many relation
          output.addInput(input);

          // append aggregator with data specific to the current input
          // real code will likely collect all input data first, then write everything to an
          // xml/json/etc file
          contributeToAggregate(input, os);
        }
      }
    }

  }

  private Serializable process(Input input) {
    // TODO Auto-generated method stub
    return null;
  }

  private void contributeToAggregate(Input input, OutputStream os) {
    // TODO Auto-generated method stub

  }

  private File getOutputFile() {
    // TODO Auto-generated method stub
    return null;
  }

  private boolean isInteresting(Input input) {
    // TODO Auto-generated method stub
    return false;
  }
}
