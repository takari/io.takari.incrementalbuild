package io.takari.incrementalbuild.aggregator.internal;


import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.ResourceStatus;
import io.takari.incrementalbuild.aggregator.AggregateOutput;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputAggregator;
import io.takari.incrementalbuild.aggregator.MetadataAggregator;
import io.takari.incrementalbuild.spi.AbstractBuildContext;
import io.takari.incrementalbuild.spi.BuildContextEnvironment;
import io.takari.incrementalbuild.spi.BuildContextFinalizer;
import io.takari.incrementalbuild.spi.DefaultBuildContextState;
import io.takari.incrementalbuild.spi.DefaultOutput;
import io.takari.incrementalbuild.spi.DefaultResource;
import io.takari.incrementalbuild.workspace.Workspace;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DefaultAggregatorBuildContext extends AbstractBuildContext
    implements
      AggregatorBuildContext {

  public DefaultAggregatorBuildContext(BuildContextEnvironment configuration) {
    super(configuration);
  }

  public DefaultAggregatorBuildContext(Workspace workspace, File stateFile,
      Map<String, Serializable> configuration, BuildContextFinalizer finalizer) {
    super(workspace, stateFile, configuration, finalizer);
  }

  @Override
  public DefaultAggregateMetadata registerOutput(File outputFile, InputAggregator aggregator) {
    return new DefaultAggregateMetadata(this, registerOutput(outputFile), aggregator);
  }

  private File registerOutput(File outputFile) {
    outputFile = normalize(outputFile);
    if (isRegisteredResource(outputFile)) {
      // only allow single registrator of the same output. not sure why/if multuple will be needed
      throw new IllegalStateException("Output already registrered " + outputFile);
    }
    registerNormalizedOutput(outputFile);
    return outputFile;
  }

  @Override
  public <T extends Serializable> AggregateOutput registerOutput(File outputFile,
      MetadataAggregator<T> aggregator) {
    return new DefaultAggregateMetadata(this, registerOutput(outputFile), aggregator);
  }

  public Collection<File> associateInputs(File outputFile, File basedir,
      Collection<String> includes, Collection<String> excludes,
      MetadataAggregator<? extends Serializable> gleaner) throws IOException {
    basedir = normalize(basedir);

    List<File> inputs = new ArrayList<>();

    for (ResourceMetadata<File> inputMetadata : registerInputs(basedir, includes, excludes)) {
      File resource = inputMetadata.getResource();
      if (isProcessedResource(resource)) {
        // don't know all implications, will deal when/if anyone asks for it
        throw new IllegalStateException("Input already processed " + resource);
      }
      inputs.add(resource);
      if (gleaner != null) {
        processResource(resource);
        if (getResourceStatus(resource) != ResourceStatus.UNMODIFIED) {
          Map<String, ? extends Serializable> metadata = gleaner.glean(resource);
          if (metadata != null) {
            for (Map.Entry<String, ? extends Serializable> entry : metadata.entrySet()) {
              state.putResourceAttribute(resource, entry.getKey(), entry.getValue());
            }
          }
        } else {
          state.setResourceAttributes(resource, oldState.getResourceAttributes(resource));
        }
      }
      state.putResourceOutput(resource, outputFile);
    }

    return inputs;
  }

  public boolean aggregateIfNecessary(File outputFile, InputAggregator creator) throws IOException {
    boolean processingRequired = isEscalated();
    if (!processingRequired) {
      processingRequired = isProcessingRequired(outputFile);
    }
    if (processingRequired) {
      markProcessedResource(outputFile);
      workspace.processOutput(outputFile);
      DefaultOutput output = newOutput(outputFile);
      List<File> inputs = new ArrayList<>();
      for (Object inputFile : getOutputInputs(state, outputFile)) {
        if (!isProcessedResource(inputFile)) {
          markProcessedResource(inputFile);
        }
        inputs.add((File) inputFile);
      }
      creator.aggregate(output, inputs);
    } else {
      markUptodateOutput(outputFile);
    }
    return processingRequired;
  }

  // re-create output if any its inputs were added, changed or deleted since previous build
  private boolean isProcessingRequired(File outputFile) {
    Collection<Object> inputs = getOutputInputs(state, outputFile);
    for (Object input : inputs) {
      if (getResourceStatus(input) != ResourceStatus.UNMODIFIED) {
        return true;
      }
    }

    for (Object oldInput : getOutputInputs(oldState, outputFile)) {
      if (!inputs.contains(oldInput)) {
        return true;
      }
    }

    return false;
  }

  @Override
  protected void assertAssociation(DefaultResource<?> resource, DefaultOutput output) {
    // aggregating context supports any association between inputs and outputs
    // or, rather, there is no obviously wrong combination
  }

  public boolean aggregateIfNecessary(File outputFile, MetadataAggregator<?> aggregator)
      throws IOException {
    Map<String, Serializable> metadata = new HashMap<>();
    for (Object input : getOutputInputs(state, outputFile)) {
      putAll(metadata, getState(input).getResourceAttributes(input));
    }

    boolean processingRequired = isEscalated();
    if (!processingRequired) {
      HashMap<String, Serializable> oldMetadata = new HashMap<>();
      for (Object input : getOutputInputs(oldState, outputFile)) {
        putAll(oldMetadata, oldState.getResourceAttributes(input));
      }
      processingRequired = !Objects.equals(metadata, oldMetadata);
    }
    if (processingRequired) {
      markProcessedResource(outputFile);
      workspace.processOutput(outputFile);
      DefaultOutput output = newOutput(outputFile);
      aggregate(aggregator, output, metadata);
    } else {
      markUptodateOutput(outputFile);
    }
    return processingRequired;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void aggregate(MetadataAggregator<?> aggregator, DefaultOutput output,
      Map<String, Serializable> metadata) throws IOException {
    aggregator.aggregate(output, (Map) metadata);
  }

  private <K, V> void putAll(Map<K, V> target, Map<K, V> source) {
    if (source != null) {
      target.putAll(source);
    }
  }

  @Override
  protected void finalizeContext() throws IOException {
    for (File oldOutput : oldState.getOutputs()) {
      if (isProcessedResource(oldOutput)) {
        // processed during this build
      } else if (state.getResource(oldOutput) == null) {
        // registered but neither processed nor marked as up-to-date
        deleteOutput(oldOutput);
      } else {
        // up-to-date
        state.setResourceMessages(oldOutput, oldState.getResourceMessages(oldOutput));
        state.setResourceAttributes(oldOutput, oldState.getResourceAttributes(oldOutput));
      }
    }
  }

  private Collection<Object> getOutputInputs(DefaultBuildContextState state, File outputFile) {
    Collection<Object> inputs = state.getOutputInputs(outputFile);
    return inputs != null && !inputs.isEmpty() ? inputs : Collections.emptyList();
  }
}
