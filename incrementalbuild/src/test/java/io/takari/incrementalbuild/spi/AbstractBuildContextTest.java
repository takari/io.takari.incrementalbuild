package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.workspace.Workspace;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public abstract class AbstractBuildContextTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  protected TestBuildContext newBuildContext() {
    return newBuildContext(Collections.<String, Serializable>emptyMap());
  }

  protected TestBuildContext newBuildContext(Map<String, Serializable> config) {
    return new TestBuildContext(new File(temp.getRoot(), "buildstate.ctx"), config);
  }

  protected TestBuildContext newBuildContext(Workspace workspace) {
    return new TestBuildContext(workspace, new File(temp.getRoot(), "buildstate.ctx"),
        Collections.<String, Serializable>emptyMap());
  }

  protected static <T> List<T> toList(Iterable<T> iterable) {
    if (iterable == null) {
      return null;
    }

    List<T> result = new ArrayList<T>();
    for (T t : iterable) {
      result.add(t);
    }
    return result;
  }

}
