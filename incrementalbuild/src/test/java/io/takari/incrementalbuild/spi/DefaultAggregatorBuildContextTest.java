package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.BuildContext.Input;
import io.takari.incrementalbuild.BuildContext.Output;
import io.takari.incrementalbuild.BuildContext.Severity;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext.AggregateCreator;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext.AggregateInput;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext.InputProcessor;
import io.takari.incrementalbuild.aggregator.internal.DefaultAggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.internal.DefaultAggregatorBuildContext.DefaultAggregateOutput;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;


public class DefaultAggregatorBuildContextTest extends AbstractBuildContextTest {

  private class FileIndexer implements AggregateCreator {
    public final Map<File, File> inputs = new LinkedHashMap<>();
    public final List<File> outputs = new ArrayList<>();

    @Override
    public void create(Output<File> output, Iterable<AggregateInput> inputs) throws IOException {
      outputs.add(output.getResource());
      try (BufferedWriter w =
          new BufferedWriter(new OutputStreamWriter(output.newOutputStream(), "UTF-8"))) {
        for (AggregateInput input : inputs) {
          this.inputs.put(input.getResource(), input.getBasedir());
          w.write(input.getResource().getAbsolutePath());
          w.newLine();
        }
      }
    }
  };

  @Test
  public void testBasic() throws Exception {
    File outputFile = new File(temp.getRoot(), "output");

    File basedir = temp.newFolder();
    File a = new File(basedir, "a").getCanonicalFile();
    a.createNewFile();

    // initial build
    FileIndexer indexer = new FileIndexer();
    DefaultBuildContext<?> ctx = newBuildContext();
    DefaultAggregatorBuildContext actx = new DefaultAggregatorBuildContext(ctx);
    DefaultAggregateOutput output = actx.registerOutput(outputFile);
    output.addInputs(basedir, null, null);
    output.createIfNecessary(indexer);
    ctx.commit();
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(1, indexer.outputs.size());
    Assert.assertEquals(1, indexer.inputs.size());
    Assert.assertEquals(basedir.getCanonicalFile(), indexer.inputs.get(a));

    // no-change rebuild
    indexer = new FileIndexer();
    ctx = newBuildContext();
    actx = new DefaultAggregatorBuildContext(ctx);
    output = actx.registerOutput(outputFile);
    output.addInputs(basedir, null, null);
    output.createIfNecessary(indexer);
    ctx.commit();
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(0, indexer.outputs.size());

    // no-change rebuild
    indexer = new FileIndexer();
    ctx = newBuildContext();
    actx = new DefaultAggregatorBuildContext(ctx);
    output = actx.registerOutput(outputFile);
    output.addInputs(basedir, null, null);
    output.createIfNecessary(indexer);
    ctx.commit();
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(0, indexer.outputs.size());

    // new input
    File b = new File(basedir, "b").getCanonicalFile();
    b.createNewFile();
    indexer = new FileIndexer();
    ctx = newBuildContext();
    actx = new DefaultAggregatorBuildContext(ctx);
    output = actx.registerOutput(outputFile);
    output.addInputs(basedir, null, null);
    output.createIfNecessary(indexer);
    ctx.commit();
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(1, indexer.outputs.size());

    // removed output
    a.delete();
    indexer = new FileIndexer();
    ctx = newBuildContext();
    actx = new DefaultAggregatorBuildContext(ctx);
    output = actx.registerOutput(outputFile);
    output.addInputs(basedir, null, null);
    output.createIfNecessary(indexer);
    ctx.commit();
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(1, indexer.outputs.size());

    // no-change rebuild
    indexer = new FileIndexer();
    ctx = newBuildContext();
    actx = new DefaultAggregatorBuildContext(ctx);
    output = actx.registerOutput(outputFile);
    output.addInputs(basedir, null, null);
    output.createIfNecessary(indexer);
    ctx.commit();
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(0, indexer.outputs.size());
  }

  @Test
  public void testEmpty() throws Exception {
    File outputFile = new File(temp.getRoot(), "output");
    File basedir = temp.newFolder();

    FileIndexer indexer = new FileIndexer();
    DefaultBuildContext<?> ctx = newBuildContext();
    DefaultAggregatorBuildContext actx = new DefaultAggregatorBuildContext(ctx);
    DefaultAggregateOutput output = actx.registerOutput(outputFile);
    output.addInputs(basedir, null, null);
    output.createIfNecessary(indexer);
    ctx.commit();
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(1, indexer.outputs.size());
  }

  @Test
  public void testInputStateCarryOver() throws Exception {
    File outputFile = new File(temp.getRoot(), "output");

    File basedir = temp.newFolder();
    File a = new File(basedir, "a").getCanonicalFile();
    a.createNewFile();

    // initial build
    DefaultBuildContext<?> ctx = newBuildContext();
    DefaultAggregatorBuildContext actx = new DefaultAggregatorBuildContext(ctx);
    DefaultAggregateOutput output = actx.registerOutput(outputFile);
    output.addInputs(basedir, null, null, new InputProcessor() {
      @Override
      public void process(Input<File> input) throws IOException {
        input.setAttribute("key", "value");
        input.addMessage(0, 0, "message", Severity.INFO, null);
      }
    });
    output.createIfNecessary(new FileIndexer());
    ctx.commit();

    // new input
    File b = new File(basedir, "b").getCanonicalFile();
    b.createNewFile();
    ctx = newBuildContext();
    actx = new DefaultAggregatorBuildContext(ctx);
    output = actx.registerOutput(outputFile);
    output.addInputs(basedir, null, null, new InputProcessor() {
      @Override
      public void process(Input<File> input) throws IOException {
        input.setAttribute("key", "value");
        input.addMessage(0, 0, "message", Severity.INFO, null);
      }
    });
    output.createIfNecessary(new AggregateCreator() {
      @Override
      public void create(Output<File> output, Iterable<AggregateInput> inputs) throws IOException {
        for (AggregateInput input : inputs) {
          Assert.assertEquals("value", input.getAttribute("key", String.class));
        }
      }
    });
    ctx.commit();
  }
}
