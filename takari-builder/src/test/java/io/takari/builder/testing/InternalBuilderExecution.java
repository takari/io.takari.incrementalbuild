package io.takari.builder.testing;

import java.io.File;
import java.util.List;

import org.junit.Assert;

import io.takari.builder.internal.Reflection;
import io.takari.builder.internal.model.BuilderMethod;

public class InternalBuilderExecution extends BuilderExecution {

  public InternalBuilderExecution(File projectBasedir, Class<?> builderType, String goal) {
    super(projectBasedir, builderType, goal);
  }

  public static InternalBuilderExecution builderExecution(File projectBasedir, Class<?> builder) {
    List<BuilderMethod> builders = Reflection.createBuilderClass(builder).builders();
    Assert.assertEquals("Ambiguous builder method", 1, builders.size());
    String goal = builders.get(0).annotation().name();
    return new InternalBuilderExecution(projectBasedir, builder, goal);
  }

  @Override
  public InternalBuilderExecution withStateFile(File inputStateFile) {
    super.withStateFile(inputStateFile);
    return this;
  }

  @Override
  public InternalBuilderExecution withClasspath(List<File> classpath) {
    super.withClasspath(classpath);
    return this;
  }
}
