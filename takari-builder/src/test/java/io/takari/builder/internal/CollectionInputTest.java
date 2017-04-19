package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderInputs.digest;
import static io.takari.builder.internal.TestInputBuilder.builder;
import static io.takari.maven.testing.TestMavenRuntime.newParameter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.junit.Test;

import io.takari.builder.Parameter;
import io.takari.builder.internal.BuilderInputs.*;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidConfigurationException;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidModelException;

public class CollectionInputTest {

  static CollectionValue newCollectionInput(String... values) {
    List<Value<?>> members = new ArrayList<>();
    for (String value : values) {
      members.add(new StringValue(value, String::new));
    }
    return new CollectionValue(ArrayList::new, members);
  }

  @Test
  public void testDigest() throws Exception {
    Digest digest = digest(newCollectionInput("a", "a"));

    assertEquals(digest, digest(newCollectionInput("a", "a")));
    assertNotEquals(digest, digest(newCollectionInput("b", "b")));
  }

  //
  //
  //

  static class _ListData {
    @Parameter
    List<String> list;
  }

  @Test
  public void testList() throws Exception {
    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Xpp3Dom listConfiguration = new Xpp3Dom("list");
    configuration.addChild(listConfiguration);
    listConfiguration.addChild(newParameter("value1", "value1"));
    listConfiguration.addChild(newParameter("value2", "value2"));

    CollectionValue input =
        builder().withConfiguration(configuration).build(_ListData.class, "list");

    assertEquals("value1", input.configuration.get(0).value());
    assertEquals("value2", input.configuration.get(1).value());
  }

  @Test(expected = InvalidConfigurationException.class)
  public void testList_noConfiguration() throws Exception {
    // the parameter is required, therefore its value (i.e., the list) cannot be empty
    builder().build(_ListData.class, "list");
  }

  @Test(expected = InvalidConfigurationException.class)
  public void testList_emptyConfiguration() throws Exception {
    // the parameter is required, therefore its value (i.e., the list) cannot be empty
    Xpp3Dom configuration = new Xpp3Dom("configuration");
    Xpp3Dom listConfiguration = new Xpp3Dom("list");
    configuration.addChild(listConfiguration);
    builder().withConfiguration(configuration).build(_ListData.class, "list");
  }

  //
  //
  //

  static class _ListOfListsData {
    @Parameter
    List<List<String>> list;
  }

  @Test(expected = InvalidModelException.class)
  public void testListOfLists() throws Exception {
    builder().build(_ListOfListsData.class, "list");
  }

  //
  //
  //

  static class _ListOfRawListsData {
    @SuppressWarnings("rawtypes")
    @Parameter
    List<List> list;
  }

  @Test(expected = InvalidModelException.class)
  public void testListOfRawLists() throws Exception {
    builder().build(_ListOfRawListsData.class, "list");
  }

  //
  //
  //

  static class _CommaSeparatedListData {
    @Parameter(value = {"a", "b", "c"})
    List<String> list;
  }

  @Test
  public void testCommaSeparatedList() throws Exception {
    CollectionValue input = builder().build(_CommaSeparatedListData.class, "list");

    assertEquals("a", input.configuration.get(0).value());
    assertEquals("b", input.configuration.get(1).value());
    assertEquals("c", input.configuration.get(2).value());
  }

  @Test(expected = InvalidConfigurationException.class)
  public void testCommaSeparatedList_illegalConfiguration() throws Exception {
    // both value and configuration are provided at the same time
    builder() //
        .withConfigurationXml("<list><l>element</l></list>")
        .build(_CommaSeparatedListData.class, "list");
  }

  //
  //
  //

  static class _OptionalListData {
    @Parameter(required = false)
    List<String> list = Collections.emptyList();
  }

  @Test
  public void testOptional() throws Exception {
    CollectionValue input = builder().build(_OptionalListData.class, "list");
    assertNull(input);
  }

  //
  //
  //

  static class _SupportedTypesData {
    @Parameter("1")
    List<String> list;

    @Parameter("1")
    Set<String> set;

    @Parameter("1")
    Collection<String> collection;

    @Parameter("1")
    LinkedList<String> concrete;
  }

  @Test
  public void testSupportedTypes() throws Exception {
    assertEquals(Collections.singletonList("1"),
        builder().build(_SupportedTypesData.class, "list").value());
    assertEquals(Collections.singleton("1"),
        builder().build(_SupportedTypesData.class, "set").value());
    assertEquals(Collections.singletonList("1"),
        builder().build(_SupportedTypesData.class, "collection").value());
    assertEquals(Collections.singletonList("1"),
        builder().build(_SupportedTypesData.class, "concrete").value());
  }

  //
  //
  //

  static class _ArrayData {
    @Parameter({"1", "2"})
    String[] strings;
  }

  @Test
  public void testArray() throws Exception {
    String[] strings = (String[]) builder().build(_ArrayData.class, "strings").value();
    assertThat(strings).contains("1", "2");
  }
}
