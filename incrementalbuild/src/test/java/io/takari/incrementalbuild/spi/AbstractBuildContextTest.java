package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractBuildContextTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  protected DefaultBuildContext<?> newBuildContext() {
    return newBuildContext(Collections.<String, Serializable>emptyMap());
  }

  protected DefaultBuildContext<?> newBuildContext(Map<String, Serializable> config) {
    return new TestBuildContext(new File(temp.getRoot(), "buildstate.ctx"), config);
  }

}
