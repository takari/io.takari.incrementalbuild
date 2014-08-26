package io.takari.incrementalbuild.aggregator.internal;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.Output;
import io.takari.incrementalbuild.BuildContext.OutputMetadata;
import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class DefaultAggregatorBuildContext implements AggregatorBuildContext {
  public class DefaultAggregatorOutput implements AggregateOutput {

    private final BuildContext context;
    private final Output<File> output;

    private Collection<AggregateInput> inputs = new ArrayList<>();

    private boolean processingRequired;

    public DefaultAggregatorOutput(BuildContext context, Output<File> output) {
      this.context = context;
      this.output = output;
      this.processingRequired = output.getStatus() != ResourceStatus.UNMODIFIED;
    }

    @Override
    public File getResource() {
      return output.getResource();
    }

    @Override
    public void addInputs(File basedir, Collection<String> includes, Collection<String> excludes)
        throws IOException {
      basedir = basedir.getCanonicalFile(); // TODO move to DefaultBuildContext
      for (InputMetadata<File> input : context.registerInputs(basedir, includes, excludes)) {
        processingRequired = processingRequired || input.getStatus() != ResourceStatus.UNMODIFIED;
        inputs.add(new DefaultAggregatorInput(basedir, input));
      }
    }

    @Override
    public void create(AggregateCreator processor) throws IOException {
      if (!processingRequired) {
        for (InputMetadata<File> input : output.getAssociatedInputs(File.class)) {
          if (input.getStatus() != ResourceStatus.UNMODIFIED) {
            processingRequired = true;
            break;
          }
        }
      }
      if (processingRequired) {
        processor.create(output, inputs);
      }
    }
  }


  public class DefaultAggregatorInput implements AggregateInput {
    private final File basedir;

    private final InputMetadata<File> input;

    public DefaultAggregatorInput(File basedir, InputMetadata<File> input) {
      this.basedir = basedir;
      this.input = input;
    }

    @Override
    public File getResource() {
      return input.getResource();
    }

    @Override
    public File getBasedir() {
      return basedir;
    }

    @Override
    public ResourceStatus getStatus() {
      return input.getStatus();
    }

    @Override
    public Iterable<? extends OutputMetadata<File>> getAssociatedOutputs() {
      return input.getAssociatedOutputs();
    }

    @Override
    public Input<File> process() {
      return input.process();
    }

    @Override
    public <V extends Serializable> V getAttribute(String key, Class<V> clazz) {
      return getAttribute(key, clazz);
    }

  }

  private final BuildContext context;

  @Inject
  public DefaultAggregatorBuildContext(BuildContext context) {
    this.context = context;
  }

  public DefaultAggregatorOutput registerOutput(File output) {
    return new DefaultAggregatorOutput(context, context.processOutput(output));
  }

}
