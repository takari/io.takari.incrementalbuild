package io.takari.builder.internal;

import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.takari.builder.Builder;
import io.takari.builder.Dependencies;
import io.takari.builder.DependencyResources;
import io.takari.builder.InputDirectory;
import io.takari.builder.Messages;
import io.takari.builder.Parameter;
import io.takari.builder.ResolutionScope;
import io.takari.builder.internal.model.AbstractParameter;
import io.takari.builder.internal.model.BuilderMethod;
import io.takari.builder.internal.model.BuilderValidationVisitor;

public class BuilderValidationVisitorTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @SuppressWarnings("serial")
  private static class ParameterValidationException extends RuntimeException {

    public ParameterValidationException(AbstractParameter parameter, String message) {
      super(message);
    }

  }

  @SuppressWarnings("serial")
  private static class BuilderValidationException extends RuntimeException {

    public BuilderValidationException(String message) {
      super(message);
    }

  }

  private BuilderValidationVisitor testee = new BuilderValidationVisitor() {
    @Override
    protected void error(AbstractParameter parameter, String message) {
      throw new ParameterValidationException(parameter, message);
    }

    @Override
    protected void error(BuilderMethod builder, String message) {
      throw new BuilderValidationException(message);
    }
  };

  private void validate(Class<?> type) {
    Reflection.createBuilderClass(type).accept(testee);
  }

  //
  //
  //

  static class _AmbiguousParameterAnnotations {
    @Parameter
    @InputDirectory(includes = "**/*")
    File parameter;
  }

  @Test
  public void testAmbiguousParameterAnnotations() throws Exception {

    thrown.expect(ParameterValidationException.class);
    thrown.expectMessage("ambigous parameter annotation present: [InputDirectory, Parameter]");

    validate(_AmbiguousParameterAnnotations.class);
  }

  //
  //
  //

  static interface CustomList<E> extends List<E> {
  }

  static class _UnsupportedMultivalueType {
    @Parameter
    CustomList<String> list;
  }

  @Test
  public void testUnsupportedMultivalueType() throws Exception {

    thrown.expect(ParameterValidationException.class);
    thrown.expectMessage(
        "multivalue prarmeter type must be concrete type or one of Collection, List and Set");

    validate(_UnsupportedMultivalueType.class);
  }

  //
  //
  //

  static class _UnsupportedInputDirectoryType {
    @InputDirectory(includes = "**/*")
    Date directory;
  }

  @Test
  public void testUnsupportedInputDirectoryType() throws Exception {

    thrown.expect(ParameterValidationException.class);
    thrown.expectMessage("@InputDirectory paramerer must be of type File");

    validate(_UnsupportedInputDirectoryType.class);
  }

  //
  //
  //

  static class _AmbiguousDependencyResolutionScope {
    @Dependencies(scope = ResolutionScope.COMPILE)
    List<File> dependencies;

    @DependencyResources(scope = ResolutionScope.RUNTIME)
    List<URL> dependencyResources;
  }

  @Test
  public void testAmbiguousDependencyResolutionScope() throws Exception {
    thrown.expect(ParameterValidationException.class);
    thrown.expectMessage("ambiguous resolution scope configuration");

    validate(_AmbiguousDependencyResolutionScope.class);
  }

  //
  //
  //

  static class _UnsupportedBuilderMethodParameters {
    @Builder(name = "builder")
    public void method(Messages message) {}
  }

  @Test
  public void testUnsupportedBuilderMethodParameters() throws Exception {
    thrown.expect(BuilderValidationException.class);
    thrown.expectMessage("Buidler method must not take parameters");

    validate(_UnsupportedBuilderMethodParameters.class);
  }

  //
  // Unknown collection element type
  //

  static class _UnknownListElement {
    @Parameter
    List<?> list;
  }

  @Test
  public void testUnknownListElement() throws Exception {
    thrown.expect(ParameterValidationException.class);
    thrown.expectMessage("Raw Collection or wildcard Collection element type");

    validate(_UnknownListElement.class);
  }

  //
  // Unknown map key/value types
  //

  static class _UnknownMayKeyValue {
    @Parameter
    Map<?, ?> map;
  }

  @Test
  public void testUnknownMapKeyValue() throws Exception {
    thrown.expect(ParameterValidationException.class);
    thrown.expectMessage("Raw Map or wildcard Map key and/or value types");

    validate(_UnknownMayKeyValue.class);
  }
}
