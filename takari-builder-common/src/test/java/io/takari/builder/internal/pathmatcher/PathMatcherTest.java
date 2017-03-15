package io.takari.builder.internal.pathmatcher;

import static org.junit.Assert.assertEquals;

import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

import io.takari.builder.internal.pathmatcher.PathMatcher;
import io.takari.builder.internal.pathmatcher.PathMatcher.Builder;
import io.takari.builder.internal.pathmatcher.PathNormalizer;

public class PathMatcherTest {
  private PathNormalizer normalizer = new PathNormalizer(Paths.get("/"));

  @Test
  public void testDirectoryMatch() {
    PathMatcher matcher = builder().excludeRoot() //
        .includePrefix("/foo/bar") //
        .build();

    Assert.assertTrue(matcher.includes("/foo/bar"));
    Assert.assertTrue(matcher.includes("/foo/bar/munchy"));

    Assert.assertFalse(matcher.includes("/foo"));
    Assert.assertFalse(matcher.includes("/blah"));
  }

  @Test
  public void testFileMatch() {
    PathMatcher matcher = builder().excludeRoot() //
        .includePath("/foo/bar") //
        .build();

    Assert.assertTrue(matcher.includes("/foo/bar"));
    Assert.assertFalse(matcher.includes("/foo/bar/munchy"));
  }

  @Test
  public void testMatcherInheritance() {
    PathMatcher matcher = builder().addMatcher(builder().includePrefix("/blah").build()) //
        .includePath("/foo/bar") //
        .build();

    Assert.assertTrue(matcher.includes("/foo/bar"));
    Assert.assertTrue(matcher.includes("/blah/blarg"));
  }

  @Test
  public void testDirectoryMatch_trailingSlash() {
    PathMatcher matcher = builder().includePrefix("/blah/").build();
    Assert.assertTrue(matcher.includes("/blah/blarg"));
  }

  @Test
  public void testFileMatch_trailingSlash() {
    PathMatcher matcher = builder().includePath("/foo/bar") //
        .includePath("/foo/bar/munchy") //
        .build();
    Assert.assertTrue(matcher.includes("/foo/bar"));
  }

  @Test
  public void testMatchParent() {
    PathMatcher matcher = builder().includePath("/foo").build();
    Assert.assertTrue(matcher.includes("/foo/bar/.."));
  }

  @Test
  public void testMatchEverythingUnderRoot() {
    PathMatcher matcher = builder().includeRoot().build();
    Assert.assertTrue(matcher.includes("/foo/bar/.."));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testNoRootRule() {
    PathMatcher matcher = builder().includePrefix("/foo/bar") //
        .build();
    matcher.includes("/foo");
  }

  @Test
  public void testMatchingRule() {
    PathMatcher matcher = builder().includePath("/foo/bar") //
        .includePrefix("/foo/bar") //
        .build();

    assertEquals("+/foo/bar", matcher.getMatchingRule("/foo/bar"));
    assertEquals("+/foo/bar/**", matcher.getMatchingRule("/foo/bar/munchy"));
  }

  @Test
  public void testVisitor() {
    // individual rule types
    assertEquals("+/", toString(builder().includeRoot().build()));
    assertEquals("-/", toString(builder().excludeRoot().build()));
    assertEquals("+/foo/dir/", toString(builder().includePrefix("/foo/dir").build()));
    assertEquals("-/foo/dir/", toString(builder().excludePrefix("/foo/dir").build()));
    assertEquals("+/foo/file", toString(builder().includePath("/foo/file").build()));
    assertEquals("-/foo/file", toString(builder().excludePath("/foo/file").build()));

    // mixture of few rules commonly used together
    StringBuilder sb = new StringBuilder();
    builder() //
        .includeRoot() //
        .includePrefix("/dir") //
        .includePath("/dir/path") //
        .excludePrefix("/dir/exclude") //
        .build().traverse((i, p) -> sb.append(i ? "+" : "-").append(p).append('\n'));
    assertEquals("+/\n" //
        + "+/dir/\n" //
        + "+/dir/path\n" //
        + "-/dir/exclude/\n", //
        sb.toString());
  }

  private Builder builder() {
    return PathMatcher.builder(normalizer);
  }

  private String toString(PathMatcher matcher) {
    StringBuilder sb = new StringBuilder();
    matcher.traverse((i, p) -> sb.append(i ? "+" : "-").append(p));
    return sb.toString();
  }
}
