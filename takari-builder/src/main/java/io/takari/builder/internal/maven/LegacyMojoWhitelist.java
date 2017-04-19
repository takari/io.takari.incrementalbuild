package io.takari.builder.internal.maven;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.MojoExecutionListener;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;

@Named
@SessionScoped
public class LegacyMojoWhitelist implements MojoExecutionListener {

  static final Charset UTF_8 = Charset.forName("UTF-8");

  public static final String FILE_WHITELIST = ".mvn/mojo-whitelist.config";

  final Path configFile;

  final Set<String> whitelist;

  @Inject
  public LegacyMojoWhitelist(MavenSession session) throws IOException, MojoExecutionException {
    this(session.getRequest().getMultiModuleProjectDirectory() != null ?
        Paths.get(session.getRequest().getMultiModuleProjectDirectory().getCanonicalPath(), FILE_WHITELIST): null);
  }

  LegacyMojoWhitelist(Path configFile) throws IOException, MojoExecutionException {
    this.configFile = configFile;
    if (configFile == null) {
      this.whitelist = null;
      return;
    }
    Set<String> whitelist = null;
    try (BufferedReader r = Files.newBufferedReader(configFile)) {
      whitelist = new LinkedHashSet<>();
      int lineno = 0;
      String str;
      while ((str = r.readLine()) != null) {
        if (str.startsWith("#")) {
          // a comment, skip
          continue;
        }

        str = str.trim();

        if (str.isEmpty()) {
          // an empty line, skip
          continue;
        }

        StringTokenizer st = new StringTokenizer(str, ":");
        try {
          String groupId = st.nextToken();
          String artifactId = st.nextToken();
          String goal = st.nextToken();
          if (st.hasMoreTokens()) {
            throw newMojoExecutionException(lineno, str);
          }
          whitelist.add(key(groupId, artifactId, goal));
        } catch (NoSuchElementException e) {
          throw newMojoExecutionException(lineno, str);
        }
      }
    } catch (FileNotFoundException | NoSuchFileException expected) {
      // this is expected and results in this.whitelist == null
    }
    this.whitelist = whitelist != null ? Collections.unmodifiableSet(whitelist) : null;
  }

  private MojoExecutionException newMojoExecutionException(int lineno, String line) {
    String msg = String.format(
        "Invalid %s:%d configuration, expected <groupId>:<artifactId>:<goal>, found %s",
        FILE_WHITELIST, lineno, line);
    return new MojoExecutionException(msg);
  }

  private String key(String groupId, String artifactId, String goal) {
    return groupId + ":" + artifactId + ":" + goal;
  }

  @Override
  public void beforeMojoExecution(MojoExecutionEvent event) throws MojoExecutionException {
    if (whitelist == null) {
      // enforcement is off by default
      return;
    }

    // note that Maven calls here only for projects that have takari-builder extension
    // enabled either directly or through one of parent projects.
    
    MojoExecution execution = event.getExecution();

    if (execution.getLifecyclePhase() == null) {
      // don't enforce direct plugin executions
      return;
    }

    final boolean whitelisted = whitelist
        .contains(key(execution.getGroupId(), execution.getArtifactId(), execution.getGoal()));
    final boolean legacy = !AbstractIncrementalMojo.class.isInstance(event.getMojo());
    if (legacy && !whitelisted) {
      String msg = String.format("Unsupported legacy mojo %s @ %s. Whitelist file location %s",
          execution, event.getProject().getArtifactId(), configFile);
      throw new MojoExecutionException(msg);
    }
    if (!legacy && whitelisted) {
      String msg =
          String.format("Redundant whitelist entry for builder %s @ %s. Whitelist file location %s",
              execution, event.getProject().getArtifactId(), configFile);
      throw new MojoExecutionException(msg);
    }
  }

  @Override
  public void afterMojoExecutionSuccess(MojoExecutionEvent event) throws MojoExecutionException {}

  @Override
  public void afterExecutionFailure(MojoExecutionEvent event) {}

}
