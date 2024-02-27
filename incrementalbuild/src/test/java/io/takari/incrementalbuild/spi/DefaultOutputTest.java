package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.Output;
import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DefaultOutputTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private TestBuildContext newBuildContext() {
        File stateFile = new File(temp.getRoot(), "buildstate.ctx");
        return new TestBuildContext(stateFile, Collections.<String, Serializable>emptyMap());
    }

    @Test
    public void testOutputStream_createParentDirectories() throws Exception {
        File outputFile = new File(temp.getRoot(), "sub/dir/outputFile");

        TestBuildContext context = newBuildContext();
        Output<File> output = context.processOutput(outputFile);
        output.newOutputStream().close();

        Assert.assertTrue(outputFile.canRead());
    }
}
