package io.takari.builder.internal.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FilesystemWorkspaceTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testWalkFile() throws Exception {
        File basefile = temp.newFile();
        List<File> files = new ArrayList<>();
        new FilesystemWorkspace().walk(basefile, (file, lastModified, length, status) -> files.add(file));
        assertThat(files).containsOnly(basefile);
    }
}
