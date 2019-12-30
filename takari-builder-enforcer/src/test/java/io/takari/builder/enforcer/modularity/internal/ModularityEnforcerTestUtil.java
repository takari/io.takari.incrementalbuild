package io.takari.builder.enforcer.modularity.internal;


import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.project.MavenProject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import io.takari.builder.enforcer.internal.EnforcerConfig;
import io.takari.builder.enforcer.modularity.ProjectContext;
import io.takari.maven.testing.TestMavenRuntime;

public class ModularityEnforcerTestUtil {

  private static class SimpleProjectDependencyGraph implements ProjectDependencyGraph {
    private final Multimap<MavenProject, MavenProject> dependents;
    private final Multimap<MavenProject, MavenProject> dependencies;

    public SimpleProjectDependencyGraph(Multimap<MavenProject, MavenProject> dependents,
        Multimap<MavenProject, MavenProject> dependencies) {
      this.dependents = dependents;
      this.dependencies = dependencies;
    }

    @Override
    public List<MavenProject> getSortedProjects() {
      return ImmutableList.copyOf(dependents.values()); // this is probably wrong
    }

    @Override
    public List<MavenProject> getDownstreamProjects(MavenProject project, boolean transitive) {
      Collection<MavenProject> dependents = this.dependents.get(project);
      return dependents == null ? ImmutableList.of() : ImmutableList.copyOf(dependents);
    }

    @Override
    public List<MavenProject> getUpstreamProjects(MavenProject project, boolean transitive) {
      Collection<MavenProject> dependencies = this.dependencies.get(project);
      return dependencies == null ? ImmutableList.of() : ImmutableList.copyOf(dependencies);
    }

    @Override
    public List<MavenProject> getAllProjects() {
      return ImmutableList.copyOf(dependents.values()); // this is probably wrong
    }
  }

  public static class Builder {
    private final TestMavenRuntime maven;
    private final Map<File, MavenProject> projects = new LinkedHashMap<>();
    private final Multimap<MavenProject, MavenProject> projectDependents =
        LinkedHashMultimap.create();
    private final Multimap<MavenProject, MavenProject> projectDependencies =
        LinkedHashMultimap.create();
    private final EnforcerConfig.Builder configBuilder = EnforcerConfig.builder().enforce(true);

    private File reactorRoot;
    private MavenProject reactorProject;
    private boolean allowBreakingProperty;
    private boolean allowReadProperty;
    private boolean allowWriteProperty;

    public Builder(TestMavenRuntime maven) {
      this.maven = maven;
    }

    public Builder withNoEnforcement() {
      this.configBuilder.enforce(false);

      return this;
    }

    public Builder withReadException(String identifier, String path) {
      this.configBuilder.withReadException(identifier, path);

      return this;
    }

    public Builder withWriteException(String identifier, String path) {
      this.configBuilder.withWriteException(identifier, path);

      return this;
    }

    public Builder withExecException(String identifier, String command) {
      this.configBuilder.withExecException(identifier, command);

      return this;
    }

    public Builder withProjectExclusion(String project) {
      this.configBuilder.withExclusion(project);

      return this;
    }

    public Builder withReactorRoot(File reactorRoot) throws Exception {
      this.reactorRoot = reactorRoot;
      this.reactorProject = maven.readMavenProject(reactorRoot);
      this.projects.put(reactorRoot, reactorProject);
      return this;
    }

    public Builder withAllowBreakingExceptionsProperty(boolean allow) {
      this.allowBreakingProperty = allow;

      return this;
    }

    public Builder withAllowReadByDefaultProperty(boolean allow) {
      this.allowReadProperty = allow;

      return this;
    }

    public Builder withAllowWriteByDefaultProperty(boolean allow) {
      this.allowWriteProperty = allow;

      return this;
    }

    public Builder withDependentProject(File parentProjectDir, File childProjectDir)
        throws Exception {
      return withDependentProject(parentProjectDir, childProjectDir, null);
    }

    public Builder withDependentProject(File parentProjectDir, File childProjectDir,
        String artifactId) throws Exception {
      MavenProject childProject = maven.readMavenProject(childProjectDir);
      MavenProject parentProject = this.projects.get(parentProjectDir);

      if (parentProject == null) {
        throw new IllegalStateException("parent project not added yet");
      }

      if (artifactId != null) {
        childProject.setArtifactId(artifactId);
      }

      this.projects.put(childProjectDir, childProject);
      this.projectDependents.put(parentProject, childProject);
      this.projectDependencies.put(childProject, parentProject);

      return this;
    }

    public ModularityEnforcerTestUtil build() throws Exception {
      if (reactorRoot == null) {
        throw new IllegalStateException("reactorRoot required");
      }

      MavenSession session = maven.newMavenSession(reactorProject);
      session.setAllProjects(new ArrayList<MavenProject>(projects.values())); // TODO this is bug in
                                                                              // test harness
      session.setProjectDependencyGraph(
          new SimpleProjectDependencyGraph(projectDependents, projectDependencies)); // another bug

      session.getUserProperties().put(SessionConfig.ALLOW_BREAKING_PROPERTY_NAME,
          String.valueOf(allowBreakingProperty));
      session.getUserProperties().put(SessionConfig.ALLOW_READ_BY_DEFAULT_PROPERTY_NAME,
          String.valueOf(allowReadProperty));
      session.getUserProperties().put(SessionConfig.ALLOW_WRITE_BY_DEFAULT_PROPERTY_NAME,
          String.valueOf(allowWriteProperty));

      SessionConfig sessionConfig = new SessionConfig(session);
      EnforcerConfig enforcerConfig = configBuilder.build();
      DefaultProjectBasedirEnforcer enforcer =
          new DefaultProjectBasedirEnforcer(ImmutableList.of(), maven.getContainer());
      enforcer.setupMavenSession(session, sessionConfig);

      return new ModularityEnforcerTestUtil(session, reactorRoot, projects, enforcer, sessionConfig,
          enforcerConfig);
    }
  }

  private final MavenSession session;
  private final File reactorRoot;
  private final Map<File, MavenProject> projects;
  private final DefaultProjectBasedirEnforcer enforcer;
  private final SessionConfig sessionConfig;
  private final EnforcerConfig enforcerConfig;

  private ModularityEnforcerTestUtil(MavenSession session, File reactorRoot,
      Map<File, MavenProject> projects, DefaultProjectBasedirEnforcer enforcer,
      SessionConfig sessionConfig, EnforcerConfig enforcerConfig) {
    this.session = session;
    this.reactorRoot = reactorRoot;
    this.projects = projects;
    this.enforcer = enforcer;
    this.sessionConfig = sessionConfig;
    this.enforcerConfig = enforcerConfig;
  }

  public DefaultProjectBasedirEnforcer getEnforcer() {
    return enforcer;
  }

  public void setupContextForRootProject() {
    enforcer.setupProjectContext(session, projects.get(reactorRoot), sessionConfig, enforcerConfig);
  }

  public void finishContextForRootProject() {
    enforcer.finishProjectContext(reactorRoot.getAbsolutePath(), projects.get(reactorRoot),
        sessionConfig);
  }

  public void setupContextForProject(File projectDir) {
    enforcer.setupProjectContext(session, projects.get(projectDir), sessionConfig, enforcerConfig);
  }

  public void finishContextForProject(File projectDir) {
    enforcer.finishProjectContext(reactorRoot.getAbsolutePath(), projects.get(projectDir),
        sessionConfig);
  }

  public ProjectContext getProjectContext() {
    return enforcer.getContextPolicy().getProjectContext();
  }

  public boolean hasProjectContext() {
    return enforcer.getContextPolicy() != null && getProjectContext() != null;
  }

  //

}
