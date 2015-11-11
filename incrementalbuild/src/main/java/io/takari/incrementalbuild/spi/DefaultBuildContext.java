package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.workspace.Workspace;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultBuildContext extends AbstractBuildContext implements BuildContext {

  public DefaultBuildContext(BuildContextEnvironment configuration) {
    super(configuration);
  }

  protected DefaultBuildContext(Workspace workspace, File stateFile,
      Map<String, Serializable> configuration, BuildContextFinalizer finalizer) {
    super(workspace, stateFile, configuration, finalizer);
  }

  @Override
  public Collection<DefaultResource<File>> registerAndProcessInputs(File basedir,
      Collection<String> includes, Collection<String> excludes) throws IOException {
    return super.registerAndProcessInputs(basedir, includes, excludes);
  }

  @Override
  protected void finalizeContext() throws IOException {

    // only supports simple input --> output associations
    // outputs are carried over iff their input is carried over

    // TODO harden the implementation
    //
    // things can get tricky even with such simple model. consider the following
    // build-1: inputA --> outputA
    // build-2: inputA unchanged. inputB --> outputA
    // now outputA has multiple inputs, which is not supported by this context
    //
    // another tricky example
    // build-1: inputA --> outputA
    // build-2: inputA unchanged before the build, inputB --> inputA
    // now inputA is both input and output, which is not supported by this context

    // multi-pass implementation
    // pass 1, carry-over up-to-date inputs and collect all up-to-date outputs
    // pass 2, carry-over all up-to-date outputs
    // pass 3, remove obsolete and orphaned outputs

    Set<File> uptodateOldOutputs = new HashSet<>();
    for (Object resource : oldState.getResources().keySet()) {
      if (oldState.isOutput(resource)) {
        continue;
      }

      if (isProcessedResource(resource) || isDeletedResource(resource)
          || !isRegisteredResource(resource)) {
        // deleted or processed resource, nothing to carry over
        continue;
      }

      if (state.isOutput(resource)) {
        // resource flipped from input to output without going through delete
        throw new IllegalStateException("Inconsistent resource type change " + resource);
      }

      // carry over

      state.putResource(resource, oldState.getResource(resource));
      state.setResourceMessages(resource, oldState.getResourceMessages(resource));
      state.setResourceAttributes(resource, oldState.getResourceAttributes(resource));

      Collection<File> oldOutputs = oldState.getResourceOutputs(resource);
      state.setResourceOutputs(resource, oldOutputs);
      if (oldOutputs != null) {
        uptodateOldOutputs.addAll(oldOutputs);
      }
    }

    for (File output : uptodateOldOutputs) {
      if (state.isResource(output)) {
        // can't carry-over registered resources
        throw new IllegalStateException("Can't carry over " + output);
      }

      state.putResource(output, oldState.getResource(output));
      state.addOutput(output);
      state.setResourceMessages(output, oldState.getResourceMessages(output));
      state.setResourceAttributes(output, oldState.getResourceAttributes(output));
    }

    for (File output : oldState.getOutputs()) {
      if (!state.isOutput(output)) {
        deleteOutput(output);
      }
    }
  }

  @Override
  public void markSkipExecution() {
    super.markSkipExecution();
  }

  @Override
  public DefaultResourceMetadata<File> registerInput(File inputFile) {
    return super.registerInput(inputFile);
  }

  @Override
  public Collection<DefaultResourceMetadata<File>> registerInputs(File basedir,
      Collection<String> includes, Collection<String> excludes) throws IOException {
    return super.registerInputs(basedir, includes, excludes);
  }

  @Override
  protected void assertAssociation(DefaultResource<?> resource, DefaultOutput output) {
    Object input = resource.getResource();
    File outputFile = output.getResource();

    // input --> output --> output2 is not supported (until somebody provides a usecase)
    if (state.isOutput(input)) {
      throw new UnsupportedOperationException();
    }

    // each output can only be associated with a single input
    Collection<Object> inputs = state.getOutputInputs(outputFile);
    if (inputs != null && !inputs.isEmpty() && !containsOnly(inputs, input)) {
      throw new UnsupportedOperationException();
    }
  }

  private static boolean containsOnly(Collection<Object> collection, Object element) {
    for (Object other : collection) {
      if (!element.equals(other)) {
        return true;
      }
    }
    return true;
  }
}
