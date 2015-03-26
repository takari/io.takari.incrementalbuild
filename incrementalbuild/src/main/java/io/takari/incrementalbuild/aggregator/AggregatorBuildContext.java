package io.takari.incrementalbuild.aggregator;


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

  public InputSet newInputSet();

}
