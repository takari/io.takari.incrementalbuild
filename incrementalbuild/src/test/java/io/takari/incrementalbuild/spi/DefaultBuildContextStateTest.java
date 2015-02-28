package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class DefaultBuildContextStateTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testRoundtrip() throws Exception {
    File file = temp.newFile();
    DefaultBuildContextState state =
        DefaultBuildContextState.withConfiguration(new HashMap<String, Serializable>());
    state.putResource(file, new FileState(file, file.lastModified(), file.length()));

    File stateFile = temp.newFile();
    try (OutputStream os = new FileOutputStream(stateFile)) {
      state.storeTo(os);
    }

    state = DefaultBuildContextState.loadFrom(stateFile);

    Assert.assertNotNull(state.getResource(file));
  }

  @Test
  public void testStateDoesNotExist() throws Exception {
    DefaultBuildContextState state =
        DefaultBuildContextState.loadFrom(new File(temp.getRoot(), "does-not-exist"));
    Assert.assertTrue(state.configuration.isEmpty());
  }

  @Test
  public void testEmptyState() throws Exception {
    Assert.assertTrue(DefaultBuildContextState.loadFrom(temp.newFile()).configuration.isEmpty());
  }

  @Test
  public void testCorruptedState() throws Exception {
    File corrupted = temp.newFile();
    Files.append("test", corrupted, Charsets.UTF_8);
    Assert.assertTrue(DefaultBuildContextState.loadFrom(corrupted).configuration.isEmpty());
  }

  @Test
  public void testIncompatibleState() throws Exception {
    File incompatible = temp.newFile();
    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(incompatible));
    oos.writeUTF("incompatible");
    oos.close();
    Assert.assertTrue(DefaultBuildContextState.loadFrom(incompatible).configuration.isEmpty());
  }
}
