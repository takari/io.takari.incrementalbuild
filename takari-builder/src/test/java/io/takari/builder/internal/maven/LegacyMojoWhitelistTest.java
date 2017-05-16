package io.takari.builder.internal.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class LegacyMojoWhitelistTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testWhitelistParse() throws Exception {
    LegacyMojoWhitelist testee = new LegacyMojoWhitelist(newConfig("g:a:g1", "g:a:g2"));

    assertThat(testee.whitelist).containsKeys("g:a:g1", "g:a:g2");
    assertThat(testee.whitelist.get("g:a:g1")).isEmpty();
    assertThat(testee.whitelist.get("g:a:g2")).isEmpty();
  }

  @Test
  public void testWhitelistExecutionParse() throws Exception {
    LegacyMojoWhitelist testee =
        new LegacyMojoWhitelist(newConfig("g:a:g1:e:pg:pa", "g:a:g2", "g:a:g1:e1:pg1:pa1"));

    assertThat(testee.whitelist).containsKeys("g:a:g1", "g:a:g2");
    assertThat(testee.whitelist.get("g:a:g1")).contains("e:pg:pa", "e1:pg1:pa1");
    assertThat(testee.whitelist.get("g:a:g2")).isEmpty();
  }

  @Test
  public void testWhitelistParse_configDoesNotExist() throws Exception {
    LegacyMojoWhitelist testee =
        new LegacyMojoWhitelist(Paths.get(temp.getRoot().getCanonicalPath(), "no-such-file"));

    assertThat(testee.whitelist).isNull();
  }

  @Test
  public void testWhitelistParse_comments() throws Exception {
    LegacyMojoWhitelist testee = new LegacyMojoWhitelist(newConfig("#", "", "#"));

    assertThat(testee.whitelist).isEmpty();
  }

  @Test
  public void testWhitelistParse_tooFewTokens() throws Exception {
    thrown.expect(MojoExecutionException.class);

    new LegacyMojoWhitelist(newConfig("g:a"));
  }

  @Test
  public void testWhitelistParse_tooManyTokens() throws Exception {
    thrown.expect(MojoExecutionException.class);

    new LegacyMojoWhitelist(newConfig("a:b:c:d"));
  }

  @Test
  public void testExecutionWhitelist() throws Exception {
    LegacyMojoWhitelist testee = new LegacyMojoWhitelist(newConfig("g:a:g1:e:pg:pa"));

    assertTrue(testee.isExecutionWhitelisted("g:a:g1", "e", "pg", "pa"));

    assertFalse(testee.isExecutionWhitelisted("g:a:g1", "e1", "pg", "pa"));
    assertFalse(testee.isExecutionWhitelisted("g:a:g1", "e", "pg1", "pa"));
    assertFalse(testee.isExecutionWhitelisted("g:a:g1", "e", "pg", "pa1"));
  }

  @Test
  public void testExecutionWhitelistNoExecutionSpecified() throws Exception {
    LegacyMojoWhitelist testee = new LegacyMojoWhitelist(newConfig("g:a:g1"));

    assertTrue(testee.isExecutionWhitelisted("g:a:g1", "e", "pg", "pa"));
  }

  @Test
  public void testExecutionWhitelistWildcard() throws Exception {
    LegacyMojoWhitelist testee = new LegacyMojoWhitelist(newConfig("g:a:g1:e:*:*"));

    assertTrue(testee.isExecutionWhitelisted("g:a:g1", "e", "pg", "pa"));
    assertTrue(testee.isExecutionWhitelisted("g:a:g1", "e", "pg1", "pa"));
    assertTrue(testee.isExecutionWhitelisted("g:a:g1", "e", "pg", "pa1"));

    assertFalse(testee.isExecutionWhitelisted("g:a:g1", "e1", "pg", "pa"));
  }

  private Path newConfig(String... lines) throws IOException {
    Path file = temp.newFile().toPath();
    try (BufferedWriter w = Files.newBufferedWriter(file, LegacyMojoWhitelist.UTF_8)) {
      for (String line : lines) {
        w.write(line);
        w.newLine();
      }
    }
    return file;
  }
}
