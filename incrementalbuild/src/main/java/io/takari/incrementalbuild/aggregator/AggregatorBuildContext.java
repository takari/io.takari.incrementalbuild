package io.takari.incrementalbuild.aggregator;

import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.InputMetadata;
import io.takari.incrementalbuild.BuildContext.Output;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Convenience interface to create aggregate outputs
 * <p>
 * Aggregate output is an output that includes information from multiple inputs. For example, jar
 * archive is an aggregate of all archive entries. Maven plugin descriptor, i.e. the
 * META-INF/maven/plugin.xml file, is an aggregate of all Mojo implementations in the Maven plugin.
 * <p>
 * Intended usage
 * 
 * <pre>
 *    {@code @}Inject AggregatorBuildContext context;
 * 
 *    AggregateOutput output = context.registerOutput(outputFile);
 *    output.associateInputs(sourceDirectory, includes, excludes);
 *    output.create(new AggregateCreator() {
 *       {@code @}Override
 *       public void create(Output<File> output, Iterable<AggregatorInput> inputs) throws IOException {
 *          // create the aggregate
 *       }
 *    });
 * </pre>
 */
public interface AggregatorBuildContext {

  /**
   * Aggregation function. Only called when new output needs to be generated.
   */
  public static interface AggregateCreator {

    /**
     * Creates aggregate output given the inputs.
     */
    public void create(Output<File> output, Iterable<AggregateInput> inputs) throws IOException;
  }

  /**
   * Aggregate input processor. Useful to glean information from input resource and store it in
   * Input attributes.
   */
  public static interface InputProcessor {
    public void process(Input<File> input) throws IOException;
  }

  /**
   * Represents aggregate output being created.
   */
  public static interface AggregateOutput {

    public File getResource();

    /**
     * Creates the aggregate if there are new, changed or removed inputs.
     * 
     * @returns {@code true} if the new output was created, {@code false} if the output was
     *          up-to-date
     */
    public boolean createIfNecessary(AggregateCreator creator) throws IOException;

    /**
     * Adds inputs to the aggregate
     */
    public void addInputs(File basedir, Collection<String> includes, Collection<String> excludes,
        InputProcessor... processors) throws IOException;
  }

  // TODO this will go away once #getRelativePath moves to InputMetadata
  public static interface AggregateInput extends InputMetadata<File> {

    /**
     * When input was registered using glob matching, returns base directory of the match.
     */
    public File getBasedir();
  }

  /**
   * Registers aggregate output with the build context.
   */
  public AggregateOutput registerOutput(File output);

}
