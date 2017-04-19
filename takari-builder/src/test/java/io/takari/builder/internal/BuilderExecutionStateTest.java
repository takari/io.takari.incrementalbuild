package io.takari.builder.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.ImmutableSet;

public class BuilderExecutionStateTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void testBackwardsCompatibility() throws Exception {
    File file = temp.newFile();

    Set<String> paths = ImmutableSet.of("a", "b", "c");
    try (ObjectOutputStream os =
        new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
      BuilderExecutionState.writeOutputPaths(os, paths);
      os.writeDouble(42); // lets hope we never actually write doubles to the state
    }

    BuilderExecutionState state = BuilderExecutionState.load(file.toPath());
    assertThat(state.outputPaths).isEqualTo(paths);
  }
}
