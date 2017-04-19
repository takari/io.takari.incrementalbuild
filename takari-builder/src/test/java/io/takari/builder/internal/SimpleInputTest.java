package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderInputs.digest;
import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.net.URI;
import java.net.URL;

import org.junit.Test;

import io.takari.builder.Parameter;
import io.takari.builder.internal.BuilderInputs.Digest;
import io.takari.builder.internal.BuilderInputs.StringValue;
import io.takari.builder.internal.BuilderInputs.Value;

public class SimpleInputTest {

  @Test
  public void testDigest() throws Exception {
    Digest digest = digest(new StringValue("a", String::new));

    assertEquals(digest, digest(new StringValue("a", String::new)));
    assertNotEquals(digest, digest(new StringValue("b", String::new)));
  }

  //
  //
  //

  static class _Data {
    @Parameter(required = false)
    String _string;

    @Parameter
    boolean _boolean;

    @Parameter(required = false)
    Boolean _Boolean;

    @Parameter
    int _int;

    @Parameter
    long _long;

    @Parameter(required = false)
    Integer _Integer;

    @Parameter(required = false)
    Long _Long;

    @Parameter(required = false)
    URL _url;

    @Parameter(required = false)
    URI _uri;
  }

  static Value<?> build(String name, String value) throws Exception {
    return builder() //
        .withConfigurationXml(String.format("<%s>%s</%s>", name, value, name)) //
        .build(_Data.class, name);
  }

  @Test
  public void testString() throws Exception {
    assertEquals("string", build("_string", "string").value());
  }

  @Test
  public void testBooleanTypes() throws Exception {
    assertEquals(true, build("_boolean", "true").value());
    assertEquals(false, build("_boolean", "false").value());
    assertEquals(true, build("_Boolean", "true").value());
    assertEquals(false, build("_Boolean", "false").value());
  }

  @Test
  public void testIntegerTypes() throws Exception {
    assertEquals(1, build("_int", "1").value());
    assertEquals(1, build("_Integer", "1").value());
    assertEquals(1L, build("_long", "1").value());
    assertEquals(1L, build("_Long", "1").value());
  }

  @Test
  public void testURL() throws Exception {
    String url = "https://www.eclipse.org";
    assertEquals(new URL(url), build("_url", url).value());
  }

  @Test
  public void testURI() throws Exception {
    String url = "https://www.eclipse.org";
    assertEquals(new URI(url), build("_uri", url).value());
  }
}
