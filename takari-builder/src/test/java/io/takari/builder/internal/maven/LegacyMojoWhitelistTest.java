package io.takari.builder.internal.maven;

import static org.assertj.core.api.Assertions.assertThat;

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

    assertThat(testee.whitelist).contains("g:a:g1", "g:a:g2");
  }

  @Test
  public void testWhitelistParse_configDoesNotExist() throws Exception {
    LegacyMojoWhitelist testee = new LegacyMojoWhitelist(Paths.get(temp.getRoot().getCanonicalPath(), "no-such-file"));

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
