package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderInputs.digest;
import static io.takari.builder.internal.DirAssert.assertFiles;
import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.InputDirectory;
import io.takari.builder.Parameter;
import io.takari.builder.internal.BuilderInputs.CollectionValue;
import io.takari.builder.internal.BuilderInputs.CompositeValue;
import io.takari.builder.internal.BuilderInputs.Digest;
import io.takari.builder.internal.BuilderInputs.InputDirectoryValue;
import io.takari.builder.internal.BuilderInputs.StringValue;
import io.takari.builder.internal.BuilderInputs.Value;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidModelException;

public class CompositeInputTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @SuppressWarnings("unchecked")
  static <T extends Value<?>> T getMember(CompositeValue input, Class<?> type, String name)
      throws ReflectiveOperationException {
    return (T) input.configuration.get(type.getDeclaredField(name));
  }

  //
  //
  //

  static class _Composite {
    public String string;

    @InputDirectory(defaultValue = ".", includes = "**/*.txt")
    public File directory;
  }

  static CompositeValue newCompositeInput(String n1, String v1, String n2, String v2)
      throws Exception {
    Map<Field, Value<?>> members = new HashMap<>();
    members.put(_Composite.class.getField(n1), new StringValue(v2, String::new));
    members.put(_Composite.class.getField(n2), new StringValue(v2, String::new));
    return new CompositeValue(_Composite.class, members);
  }

  //
  //
  //

  @Test
  public void testDigest() throws Exception {
    Digest digest = digest(newCompositeInput("string", "a", "directory", "a"));

    assertEquals(digest, digest(newCompositeInput("string", "a", "directory", "a")));
    assertNotEquals(digest, digest(newCompositeInput("string", "b", "directory", "b")));
  }

  //
  //
  //

  static class _Data {
    @Parameter
    private _Composite parameter;
  }

  @Test
  public void testComplexParameter() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    new File(basedir, "1.txt").createNewFile();

    CompositeValue input = builder(basedir) //
        .withConfigurationXml("<parameter><string>string-value</string></parameter>") //
        .build(_Data.class, "parameter");

    StringValue string = getMember(input, _Composite.class, "string");
    InputDirectoryValue directory = getMember(input, _Composite.class, "directory");

    assertEquals("string-value", string.configuration);
    assertFiles(directory.files(), new File(basedir, "1.txt"));
  }

  //
  //
  //

  static class _ListData {
    @Parameter
    public List<_Composite> parameter;
  }

  @Test
  public void testComplexListParameter() throws Exception {
    File basedir = temp.newFolder().getCanonicalFile();
    new File(basedir, "dir-1").mkdirs();
    new File(basedir, "dir-1/1.txt").createNewFile();
    new File(basedir, "dir-2").mkdirs();
    new File(basedir, "dir-2/2.txt").createNewFile();

    CollectionValue list = builder(basedir) //
        .withConfigurationXml("<parameter>" //
            + "<entry><string>entry-1</string><inputDirectory><location>dir-1</location></inputDirectory></entry>" //
            + "<entry><string>entry-2</string><inputDirectory><location>dir-2</location></inputDirectory></entry>" //
            + "</parameter>") //
        .build(_ListData.class, "parameter");

    assertEquals(2, list.configuration.size());

    CompositeValue composite1 = (CompositeValue) list.configuration.get(0);
    assertEquals(_Composite.class, composite1.type);

    InputDirectoryValue directory1 = getMember(composite1, _Composite.class, "directory");

    assertEquals("entry-1",
        ((StringValue) getMember(composite1, _Composite.class, "string")).configuration);
    assertFiles(directory1.files(), //
        new File(basedir, "dir-1/1.txt"), //
        new File(basedir, "dir-2/2.txt"));

    // check the value can be created
    assertEquals(2, ((List<?>) list.value()).size());
  }

  //
  //
  //

  static class _OptionalCompositeParameterData {
    @Parameter(required = false)
    _Composite parameter;
  }

  @Test
  public void testOptionalCompositeParameter() throws Exception {
    assertNull(builder().build(_OptionalCompositeParameterData.class, "parameter"));
  }

  //
  //
  //

  static class _PrimitiveMemberComposite {
    String string;
    int member = 123;
  }

  static class _PrimitiveMemberData {
    @Parameter
    _PrimitiveMemberComposite parameter;
  }

  @Test
  public void testPrimitiveMemberComposite() throws Exception {
    // Not sure about expected/desired behaviour here
    // On one hand, "required" suggests the parameter value must be not-empty at runtime, which
    // can be interpreted that configuration value should be provided.
    // On the other hand, parameter type instance can be created without explicit configuration,
    // which satisfies "required" requirement, at least technically.

    _PrimitiveMemberComposite parameter = (_PrimitiveMemberComposite) builder() //
        .withConfigurationXml("<parameter><string>string</string></parameter>") //
        .build(_PrimitiveMemberData.class, "parameter").value();
    assertEquals(123, parameter.member);
  }

  //
  //
  //

  static class _IllegalAnnotationValueData {
    @Parameter(value = "illegal")
    _Composite value;

    @Parameter(defaultValue = "illegal")
    _Composite defaultValue;
  }

  @Test(expected = InvalidModelException.class)
  public void testIllegalAnnotationValue() throws Exception {
    builder() //
        .withConfigurationXml("<value><string>s</string><directory>.</directory></value>") //
        .build(_IllegalAnnotationValueData.class, "value");
  }

  @Test(expected = InvalidModelException.class)
  public void testIllegalAnnotationDefaultValue() throws Exception {
    builder() //
        .withConfigurationXml(
            "<defaultValue><string>s</string><directory>.</directory></defaultValue>") //
        .build(_IllegalAnnotationValueData.class, "defaultValue");
  }


  //
  //
  //

  static class _PrivateMemberComposite {
    private String privateMember;

    public String getPrivateMember() {
      return privateMember;
    }
  }

  static class _PrivateMemberCompositeData {
    @Parameter
    _PrivateMemberComposite parameter;
  }

  @Test
  public void testPrivateMember() throws Exception {
    _PrivateMemberComposite parameter = (_PrivateMemberComposite) builder() //
        .withConfigurationXml("<parameter><privateMember>value</privateMember></parameter>") //
        .build(_PrivateMemberCompositeData.class, "parameter").value();
    assertEquals("value", parameter.getPrivateMember());
  }
}
