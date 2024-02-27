package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DefaultBasicBuildContextTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private DefaultBasicBuildContext newContext() {
        File stateFile = new File(temp.getRoot(), "buildstate.ctx");
        return new DefaultBasicBuildContext(
                new FilesystemWorkspace(), stateFile, new HashMap<String, Serializable>(), null);
    }

    @Test
    public void testDeletedOutput() throws Exception {
        File input = temp.newFile();
        File output = temp.newFile();

        DefaultBasicBuildContext ctx;

        ctx = newContext();
        ctx.registerInput(input);
        ctx.processOutput(output);
        ctx.commit(null);

        output.delete();

        ctx = newContext();
        ctx.registerInput(input);
        ctx.commit(null);
    }
}
