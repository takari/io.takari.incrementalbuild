package io.takari.incrementalbuild.util;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.takari.incrementalbuild.ResourceStatus;
import java.io.File;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class URLResourceHolderTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testGetStatus() throws Exception {
        File file = temp.newFile();

        URLResourceHolder a = new URLResourceHolder(file.toURI().toURL());
        Assert.assertEquals(ResourceStatus.UNMODIFIED, a.getStatus());

        Files.append("test", file, Charsets.UTF_8);
        Assert.assertEquals(ResourceStatus.MODIFIED, a.getStatus());

        Assert.assertTrue(file.delete());
        Assert.assertEquals(ResourceStatus.REMOVED, a.getStatus());
    }
}
