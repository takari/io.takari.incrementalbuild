package io.takari.incremental.test;

import io.takari.incremental.internal.DefaultBuildContext;

import java.io.File;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class DefaultBuildContextTest {

  @Rule
  public final TestName name = new TestName();

  private DefaultBuildContext newBuildContext() {
    File stateFile =
        new File("target/", getClass().getSimpleName() + "_" + name.getMethodName() + ".ctx");
    return new DefaultBuildContext(stateFile, Collections.<String, byte[]>emptyMap());
  }

  @Test
  public void testRegisterInput_inputFileDoesNotExist() throws Exception {
    File file = new File("target/does_not_exist");
    Assert.assertTrue(!file.exists() && !file.canRead());
    Assert.assertNull(newBuildContext().registerInput(file));
  }

  public void testRegisterInput() throws Exception {
    File file = new File("src/test/resources/simplelogger.properties");
    Assert.assertTrue(file.exists() && file.canRead());
    DefaultBuildContext context = newBuildContext();
    Assert.assertSame(context.registerInput(file), context.registerInput(file));
  }
}
