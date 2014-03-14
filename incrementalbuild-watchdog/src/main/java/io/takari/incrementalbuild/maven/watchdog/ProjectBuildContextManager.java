package io.takari.incrementalbuild.maven.watchdog;

import io.takari.incrementalbuild.maven.internal.MavenIncrementalConventions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.MojoExecutionListener;
import org.apache.maven.execution.ProjectExecutionEvent;
import org.apache.maven.execution.ProjectExecutionListener;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.Lifecycle;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecution.Source;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;

/**
 * Detects and cleans up mojo executions removed since the last clean build.
 * <p>
 * This is optional part of Maven incremental build support. To enable, add buildavoidance build
 * extension.
 */
@Named
@Singleton
public class ProjectBuildContextManager implements ProjectExecutionListener, MojoExecutionListener {

  // dies with class not found error if UTF-8 charset is not present
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  /**
   * MavenProject context key that maps to per-build-phase list of mojo execution ids
   */
  private static final String CTX_EXECUTIONS = "buildavoidance.executions";

  /**
   * MavenProject context key that maps to the id of the first mojo execution of default lifecycle
   */
  private static final String CTX_DEFAULT_LIFECYCLE_START =
      "buildavoidance.default-lifecycle-start";

  private final Map<String, Lifecycle> phaseToLifecycleMap;

  private final MavenIncrementalConventions conventions;

  @Inject
  public ProjectBuildContextManager(DefaultLifecycles defaultLifecycles,
      MavenIncrementalConventions conventions) {
    this.conventions = conventions;
    this.phaseToLifecycleMap =
        Collections.unmodifiableMap(defaultLifecycles.getPhaseToLifecycleMap());
  }

  @Override
  public void beforeProjectLifecycleExecution(ProjectExecutionEvent event)
      throws LifecycleExecutionException {
    final List<MojoExecution> executionPlan = event.getExecutionPlan();
    final MavenProject project = event.getProject();

    // for each build phase in this build
    // fail if not clean any mojos were removed since last clean build
    // merge and persist this build phases/mojos with any later phases in the previous build(s)
    // persist early to handle interrupted builds.
    // it is possible to persist before mojo execution,
    // but that is hard to synchronize if multiple mojos run in parallel

    List<MojoExecution> defaultPlan = getDefaultLifecycle(executionPlan);

    if (defaultPlan.isEmpty()) {
      return;
    }

    // construct by-phase list of mojo execution ids
    LinkedHashMap<String, List<String>> phases = new LinkedHashMap<String, List<String>>();
    for (MojoExecution execution : defaultPlan) {
      List<String> executions = phases.get(execution.getLifecyclePhase());
      if (executions == null) {
        executions = new ArrayList<String>();
        phases.put(execution.getLifecyclePhase(), executions);
      }
      executions.add(conventions.getExecutionId(execution));
    }

    if (!isCleanBuild(executionPlan)) {
      LinkedHashMap<String, List<String>> previousPhases = loadPhases(project);
      for (Map.Entry<String, List<String>> phaseEntry : phases.entrySet()) {
        List<String> previousExecutions = previousPhases.get(phaseEntry.getKey());
        if (previousExecutions == null) {
          // never ran to this phase before, no need to look for later phases
          break;
        }
        List<String> executions = phaseEntry.getValue();

        // for now, assume order within phase does not matter
        List<String> removedExecutions = new ArrayList<String>();
        for (String execution : previousExecutions) {
          if (!executions.contains(execution)) {
            removedExecutions.add(execution);
          }
        }

        if (!removedExecutions.isEmpty()) {
          StringBuilder msg = new StringBuilder();
          msg.append("Could not perform incremental build for ").append(project.toString());
          msg.append("\n   removed plugin executions since last clean build:");
          for (String execution : removedExecutions) {
            msg.append(' ').append(execution);
          }
          msg.append("\nPlease restart the build with 'clean',");
          throw new LifecycleExecutionException(msg.toString(), project);
        }
      }

      // merge any later phases from previous executions
      String lastPhase = defaultPlan.get(defaultPlan.size() - 1).getLifecyclePhase();
      Iterator<Map.Entry<String, List<String>>> previousPhaseIter =
          previousPhases.entrySet().iterator();
      while (previousPhaseIter.hasNext() && !lastPhase.equals(previousPhaseIter.next().getKey()));
      while (previousPhaseIter.hasNext()) {
        Map.Entry<String, List<String>> previousPhase = previousPhaseIter.next();
        phases.put(previousPhase.getKey(), previousPhase.getValue());
      }
    }

    // this is executed before any project mojo execution
    // state persisted here will be purged during "clean" build
    // defer state persistence until first mojo of default lifecycle

    project.setContextValue(CTX_DEFAULT_LIFECYCLE_START,
        conventions.getExecutionId(defaultPlan.get(0)));
    project.setContextValue(CTX_EXECUTIONS, phases);
  }

  private List<MojoExecution> getDefaultLifecycle(List<MojoExecution> executionPlan) {
    List<MojoExecution> result = new ArrayList<MojoExecution>();
    for (MojoExecution execution : executionPlan) {
      if ("default".equals(getLifecycleId(execution))) {
        result.add(execution);
      }
    }
    return result;
  }

  private String getLifecycleId(MojoExecution execution) {
    if (execution.getSource() == Source.LIFECYCLE) {
      Lifecycle lifecycle = phaseToLifecycleMap.get(execution.getLifecyclePhase());
      if (lifecycle != null) {
        return lifecycle.getId();
      }
    }
    return null;
  }

  private LinkedHashMap<String, List<String>> loadPhases(MavenProject project)
      throws LifecycleExecutionException {
    final LinkedHashMap<String, List<String>> phases = new LinkedHashMap<String, List<String>>();
    final File state = getExecutionsListLocation(project);
    if (state.exists()) {
      List<String> lines = new ArrayList<String>();
      try {
        // TODO replace with Files.readAllLines when we move to java7
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(new FileInputStream(state), UTF_8));
        try {
          String str;
          while ((str = reader.readLine()) != null) {
            lines.add(str);
          }
        } finally {
          IOUtil.close(reader);
        }
      } catch (IOException e) {
        throw new LifecycleExecutionException("Could not maintainer incremental build state for "
            + project, e);
      }
      for (String line : lines) {
        int idx = line.indexOf(':');
        if (idx <= 0) {
          throw new LifecycleExecutionException("Invalid file format " + state, project);
        }
        String phase = line.substring(0, idx);
        List<String> executions = phases.get(phase);
        if (executions == null) {
          executions = new ArrayList<String>();
          phases.put(phase, executions);
        }
        executions.add(line.substring(idx + 1));
      }
    }
    return phases;
  }

  private void storePhases(MavenProject project, LinkedHashMap<String, List<String>> phases)
      throws IOException {
    final File state = getExecutionsListLocation(project);
    if (!state.getParentFile().exists() && !state.getParentFile().mkdirs()) {
      throw new IOException("Could not create parent directories " + state);
    }
    BufferedWriter writer =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(state), UTF_8));
    try {
      for (Map.Entry<String, List<String>> phase : phases.entrySet()) {
        for (String execution : phase.getValue()) {
          writer.write(phase.getKey() + ":" + execution);
          writer.write('\n');
        }
      }
    } finally {
      IOUtil.close(writer);
    }
  }

  private File getExecutionsListLocation(MavenProject project) {
    return new File(conventions.getProjectStateLocation(project), "executions.lst");
  }

  private boolean isCleanBuild(List<MojoExecution> executionPlan) {
    for (MojoExecution execution : executionPlan) {
      String lifecycleId = getLifecycleId(execution);
      if (lifecycleId != null) {
        return "clean".equals(lifecycleId);
      }
    }
    return false; // only direct invocation mojos
  }

  @Override
  public void beforeMojoExecution(MojoExecutionEvent event) throws MojoExecutionException {
    final MojoExecution execution = event.getExecution();
    final MavenProject project = event.getProject();

    String defaultLifecycleStart = (String) project.getContextValue(CTX_DEFAULT_LIFECYCLE_START);
    // Seems to be null under direct invocation
    if (defaultLifecycleStart != null
        && defaultLifecycleStart.equals(conventions.getExecutionId(execution))) {
      @SuppressWarnings("unchecked")
      LinkedHashMap<String, List<String>> phases =
          (LinkedHashMap<String, List<String>>) project.getContextValue(CTX_EXECUTIONS);
      try {
        storePhases(project, phases);
      } catch (IOException e) {
        throw new MojoExecutionException("Could not maintainer incremental build state for "
            + project, e);
      }
    }
  }


  @Override
  public void beforeProjectExecution(ProjectExecutionEvent event)
      throws LifecycleExecutionException {}

  @Override
  public void afterProjectExecutionSuccess(ProjectExecutionEvent event)
      throws LifecycleExecutionException {}

  @Override
  public void afterProjectExecutionFailure(ProjectExecutionEvent event) {}

  @Override
  public void afterMojoExecutionSuccess(MojoExecutionEvent event) throws MojoExecutionException {}

  @Override
  public void afterExecutionFailure(MojoExecutionEvent event) {}
}
