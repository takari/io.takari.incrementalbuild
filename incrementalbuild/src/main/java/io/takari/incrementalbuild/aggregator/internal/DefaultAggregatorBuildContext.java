package io.takari.incrementalbuild.aggregator.internal;


import io.takari.incrementalbuild.ResourceStatus;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputAggregator;
import io.takari.incrementalbuild.aggregator.MetadataAggregator;
import io.takari.incrementalbuild.spi.AbstractBuildContext;
import io.takari.incrementalbuild.spi.BuildContextEnvironment;
import io.takari.incrementalbuild.spi.BuildContextFinalizer;
import io.takari.incrementalbuild.spi.DefaultBuildContextState;
import io.takari.incrementalbuild.spi.DefaultOutput;
import io.takari.incrementalbuild.spi.DefaultResource;
import io.takari.incrementalbuild.spi.DefaultResourceMetadata;
import io.takari.incrementalbuild.workspace.Workspace;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
  public DefaultInputSet newInputSet() {
    return new DefaultInputSet(this);
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

  private Map<String, Serializable> glean(Collection<File> inputs,
      MetadataAggregator<? extends Serializable> gleaner) throws IOException {
    String attributeKey = attributeKey(gleaner);

    Map<String, Serializable> metadata = new HashMap<>();
    for (File inputFile : inputs) {
      if (getResourceStatus(inputFile) != ResourceStatus.UNMODIFIED) {
        markProcessedResource(inputFile);
        Map<String, ? extends Serializable> gleaned = gleaner.glean(inputFile);
        if (gleaned != null) {
          metadata.putAll(gleaned);
          state.putResourceAttribute(inputFile, attributeKey, new HashMap<>(gleaned));
        }
      } else {
        Serializable persisted = oldState.getResourceAttribute(inputFile, attributeKey);
        state.putResourceAttribute(inputFile, attributeKey, persisted);
        putAll(metadata, persisted);
      }
    }

    return metadata;
  }

  private String attributeKey(MetadataAggregator<?> gleaner) {
    return gleaner.getClass().getName(); // TODO maybe add a prefix
  }

  private void associate(Iterable<File> inputs, File outputFile) {
    for (File inputFile : inputs) {
      state.putResourceOutput(inputFile, outputFile);
    }
  }

  public boolean aggregateIfNecessary(Collection<File> inputs, File outputFile,
      InputAggregator creator) throws IOException {
    outputFile = registerOutput(outputFile);
    associate(inputs, outputFile);
    boolean processingRequired = isEscalated();
    if (!processingRequired) {
      processingRequired = isProcessingRequired(inputs, outputFile);
    }
    if (processingRequired) {
      markProcessedResource(outputFile);
      workspace.processOutput(outputFile);
      DefaultOutput output = newOutput(outputFile);
      for (File inputFile : inputs) {
        if (!isProcessedResource(inputFile)) {
          markProcessedResource(inputFile);
        }
      }
      creator.aggregate(output, inputs);
    } else {
      markUptodateOutput(outputFile);
    }
    return processingRequired;
  }

  // re-create output if any its inputs were added, changed or deleted since previous build
  private boolean isProcessingRequired(Collection<File> inputs, File outputFile) {
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

  public boolean aggregateIfNecessary(Collection<File> inputs, File outputFile,
      MetadataAggregator<?> aggregator) throws IOException {
    outputFile = registerOutput(outputFile);
    associate(inputs, outputFile);
    Map<String, Serializable> metadata = glean(inputs, aggregator);
    boolean processingRequired = isEscalated();
    if (!processingRequired) {
      HashMap<String, Serializable> oldMetadata = new HashMap<>();
      for (Object input : getOutputInputs(oldState, outputFile)) {
        putAll(oldMetadata, oldState.getResourceAttribute(input, attributeKey(aggregator)));
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

  @SuppressWarnings("unchecked")
  private <K, V> void putAll(Map<K, V> target, Serializable source) {
    if (source != null) {
      target.putAll((Map<K, V>) source);
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

  @Override
  public Collection<DefaultResourceMetadata<File>> registerInputs(File basedir,
      Collection<String> includes, Collection<String> excludes) throws IOException {
    return super.registerInputs(basedir, includes, excludes);
  }
}
