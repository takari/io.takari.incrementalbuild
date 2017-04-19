package io.takari.builder.enforcer.internal;

import static io.takari.builder.enforcer.internal.EnforcerConfig.ALL_BUILDERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EnforcerConfigTest {
  
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  
  private EnforcerConfig config;
  
  @Test
  public void testWildcardForSingleBuilder() throws Exception {

    config = EnforcerConfig.builder()
        .enforce(true)
        .withReadException("*", "/some/file")
        .withReadException("project", "/some/other/file")
        .build();

    Collection<String> readExceptions = config.getReadExceptions("project");
    
    assertThat(readExceptions).containsExactlyInAnyOrder("/some/file", "/some/other/file");
  }
  
  @Test
  public void testWildcardForAllBuilders() throws Exception {
    config = EnforcerConfig.builder()
        .enforce(true)
        .withReadException("*", "/some/file")
        .withReadException("project", "/some/other/file")
        .build();
    
    Collection<String> readExceptions = config.getReadExceptions("*");
    Collection<String> writeExceptions = config.getWriteExceptions("*");
    assertThat(readExceptions).containsExactlyInAnyOrder("/some/file");
    assertThat(writeExceptions).isEmpty();
  }
  
  
  @Test
  public void testNetworkAccess() throws Exception {
    config = EnforcerConfig.builder()
        .enforce(true)
        .withNetworkException("project")
        .build();
    
    boolean canNetwork = config.allowNetworkAccess("project");
    
    assertThat(canNetwork).isTrue();
  }
  
  @Test
  public void testNoNetworkAccess() throws Exception {
    config = EnforcerConfig.builder()
        .enforce(true)
        .withNetworkException("not-project")
        .build();
    
    boolean canNetwork = config.allowNetworkAccess("project");
    
    assertThat(canNetwork).isFalse();
  }
  
  @Test
  public void testExclusion() throws Exception {
    config = EnforcerConfig.builder()
        .enforce(true)
        .withExclusion("project")
        .build();
    
    boolean exclude = config.exclude("project");
    
    assertThat(exclude).isTrue();
  }
  
  @Test
  public void testNoExclusion() throws Exception {
    config = EnforcerConfig.builder()
        .enforce(true)
        .withExclusion("not-project")
        .build();
    
    boolean exclude = config.exclude("project");
    
    assertThat(exclude).isFalse();
  }
  
  @Test
  public void testMixed() throws Exception {
    config = EnforcerConfig.builder()
        .enforce(true)
        .withReadException("project", "/some/file")
        .withWriteException("project", "/not/checking")
        .withWriteException("other", "/some/other")
        .withWriteException("other", "/additional/one")
        .withNetworkException("project")
        .build();
    
    Collection<String> projectReadExceptions = config.getReadExceptions("project");
    Collection<String> otherWriteExceptions = config.getWriteExceptions("other");
    
    assertThat(projectReadExceptions).containsExactlyInAnyOrder("/some/file");
    assertThat(otherWriteExceptions).containsExactlyInAnyOrder("/some/other", "/additional/one");
  }
  
  @Test
  public void testHasEntriesFor() throws Exception {
    config = EnforcerConfig.builder()
        .enforce(true)
        .withReadException("1", "/some/file")
        .withWriteException("2", "/not/checking")
        .withExecException("3", "/some/other")
        .withNetworkException("4")
        .withExclusion("5")
        .withExecException("*", "git")
        .build();

    assertThat(config.hasEntriesFor("1")).isTrue();
    assertThat(config.hasEntriesFor("2")).isTrue();
    assertThat(config.hasEntriesFor("3")).isTrue();
    assertThat(config.hasEntriesFor("4")).isTrue();
    assertThat(config.hasEntriesFor("5")).isTrue();
    assertThat(config.hasEntriesFor("6")).isFalse();
  }
  
  @Test
  public void testFileParsing() throws Exception {
    Path whitelistFile = temp.newFile().toPath();
    String whitelist = new StringBuilder()
        .append("# enforce\n")
        .append("1 R /file1\n")
        .append("2 RT /file2\n")
        .append("3 W /file3\n")
        .append("4 E git\n")
        .append("5 N\n")
        .append("6 P\n")
        .append("* R some/other\n")
        .toString();
    
    Files.write(whitelistFile, whitelist.getBytes());
    
    EnforcerConfig config = EnforcerConfig.fromFile(whitelistFile);
    
    assertThat(config.hasEntriesFor("1")).isTrue();
    assertThat(config.hasEntriesFor("2")).isTrue();
    assertThat(config.hasEntriesFor("3")).isTrue();
    assertThat(config.hasEntriesFor("4")).isTrue();
    assertThat(config.hasEntriesFor("5")).isTrue();
    assertThat(config.hasEntriesFor(ALL_BUILDERS)).isTrue();
    assertThat(config.hasEntriesFor("6")).isTrue();
    assertThat(config.hasEntriesFor("7")).isFalse();
  }
}
