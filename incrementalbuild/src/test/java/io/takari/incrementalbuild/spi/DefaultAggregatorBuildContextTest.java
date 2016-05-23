package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.InputAggregator;
import io.takari.incrementalbuild.aggregator.internal.DefaultAggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.internal.DefaultInputSet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;


public class DefaultAggregatorBuildContextTest extends AbstractBuildContextTest {

  private class FileIndexer implements InputAggregator {
    public final List<File> inputs = new ArrayList<>();
    public final List<File> outputs = new ArrayList<>();

    @Override
    public void aggregate(Output<File> output, Iterable<File> inputs) throws IOException {
      outputs.add(output.getResource());
      try (BufferedWriter w =
          new BufferedWriter(new OutputStreamWriter(output.newOutputStream(), "UTF-8"))) {
        for (File input : inputs) {
          this.inputs.add(input);
          w.write(input.getAbsolutePath());
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
    DefaultAggregatorBuildContext actx = newContext();
    DefaultInputSet output = actx.newInputSet();
    output.addInputs(basedir, null, null);
    output.aggregateIfNecessary(outputFile, indexer);
    actx.commit(null);
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(1, indexer.outputs.size());
    Assert.assertEquals(1, indexer.inputs.size());

    // no-change rebuild
    indexer = new FileIndexer();
    actx = newContext();
    output = actx.newInputSet();
    output.addInputs(basedir, null, null);
    output.aggregateIfNecessary(outputFile, indexer);
    actx.commit(null);
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(0, indexer.outputs.size());

    // no-change rebuild
    indexer = new FileIndexer();
    actx = newContext();

    output = actx.newInputSet();
    output.addInputs(basedir, null, null);
    output.aggregateIfNecessary(outputFile, indexer);
    actx.commit(null);
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(0, indexer.outputs.size());

    // new input
    File b = new File(basedir, "b").getCanonicalFile();
    b.createNewFile();
    indexer = new FileIndexer();
    actx = newContext();
    output = actx.newInputSet();
    output.addInputs(basedir, null, null);
    output.aggregateIfNecessary(outputFile, indexer);
    actx.commit(null);
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(1, indexer.outputs.size());

    // removed output
    a.delete();
    indexer = new FileIndexer();
    actx = newContext();
    output = actx.newInputSet();
    output.addInputs(basedir, null, null);
    output.aggregateIfNecessary(outputFile, indexer);
    actx.commit(null);
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(1, indexer.outputs.size());

    // no-change rebuild
    indexer = new FileIndexer();
    actx = newContext();
    output = actx.newInputSet();
    output.addInputs(basedir, null, null);
    output.aggregateIfNecessary(outputFile, indexer);
    actx.commit(null);
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(0, indexer.outputs.size());
  }

  private DefaultAggregatorBuildContext newContext() {
    File stateFile = new File(temp.getRoot(), "buildstate.ctx");
    return new DefaultAggregatorBuildContext(new FilesystemWorkspace(), stateFile,
        new HashMap<String, Serializable>(), null);
  }

  @Test
  public void testEmpty() throws Exception {
    File outputFile = new File(temp.getRoot(), "output");
    File basedir = temp.newFolder();

    FileIndexer indexer = new FileIndexer();
    DefaultAggregatorBuildContext actx = newContext();
    DefaultInputSet output = actx.newInputSet();
    output.addInputs(basedir, null, null);
    output.aggregateIfNecessary(outputFile, indexer);
    actx.commit(null);
    Assert.assertTrue(outputFile.canRead());
    Assert.assertEquals(1, indexer.outputs.size());
  }

  @Test
  public void testEmptyNoCreate() throws Exception {
    File outputFile = new File(temp.getRoot(), "output");
    File basedir = temp.newFolder();

    FileIndexer indexer = new FileIndexer();
    DefaultAggregatorBuildContext actx = newContext();
    DefaultInputSet output = actx.newInputSet(false);
    output.addInputs(basedir, null, null);
    output.aggregateIfNecessary(outputFile, indexer);
    actx.commit(null);
    Assert.assertFalse(outputFile.exists());
    Assert.assertEquals(0, indexer.outputs.size());
  }
}
