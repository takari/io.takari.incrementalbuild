package io.takari.builder.internal.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.junit.Rule;
import org.junit.Test;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.ResolutionScope;
import io.takari.builder.internal.cache.ScopedProjectDependencyCache;
import io.takari.builder.internal.resolver.DependencyResolver;
import io.takari.maven.testing.TestMavenRuntime;
import io.takari.maven.testing.TestResources;

public class MavenDependencyResolverTest {

  @Rule
  public final TestMavenRuntime maven = new TestMavenRuntime();

  @Rule
  public final TestResources resources = new TestResources();

  private DependencyResolver resolver;
  private File jarfile;
  private File dirfiledirect;
  private File dirfiletransitive;

  public void setup(String projectdir) throws Exception {
    File basedir = resources.getBasedir("dependency-resolution");
    File projectfile = new File(basedir, projectdir);
    File repodir = new File(basedir, "repository");
    jarfile = new File(repodir, "junit/junit/4.12/junit-4.12.jar");
    MavenProject directProject = maven.readMavenProject(new File(basedir, "direct"));
    MavenProject transitiveProject = maven.readMavenProject(new File(basedir, "transitive"));
    dirfiledirect = new File(directProject.getBuild().getOutputDirectory());
    dirfiletransitive = new File(transitiveProject.getBuild().getOutputDirectory());
    MavenProject project = maven.readMavenProject(projectfile);
    RepositorySystem repoSystem = maven.lookup(RepositorySystem.class);
    LocalRepository localRepo = new LocalRepository(repodir);
    DefaultRepositorySystemSession repoSession = MavenRepositorySystemUtils.newSession();
    WorkspaceReader reader =
        new TestWorkspaceReader(Arrays.asList(directProject, transitiveProject));
    repoSession.setWorkspaceReader(reader);
    LocalRepositoryManager lrm = repoSystem.newLocalRepositoryManager(repoSession, localRepo);
    repoSession.setLocalRepositoryManager(lrm);
    resolver = new MavenDependencyResolver(project, repoSession, repoSystem,
        new ScopedProjectDependencyCache());
  }

  private class TestWorkspaceReader implements WorkspaceReader {
    private final Map<String, MavenProject> projectsByGAV = new HashMap<>();
    private final Map<String, MavenProject> projectsByGA = new HashMap<>();
    private final WorkspaceRepository repository =
        new WorkspaceRepository("reactor", new HashSet<>(projectsByGAV.keySet()));

    private TestWorkspaceReader(List<MavenProject> projects) throws Exception {
      projects.forEach(p -> {
        putProject(p);
        putVersionlessProject(p);
      });
    }

    private void putProject(MavenProject p) {
      projectsByGAV.put(ArtifactUtils.key(p.getGroupId(), p.getArtifactId(), p.getVersion()), p);
    }

    private void putVersionlessProject(MavenProject p) {
      projectsByGA.put(ArtifactUtils.versionlessKey(p.getGroupId(), p.getArtifactId()), p);
    }

    @Override
    public WorkspaceRepository getRepository() {
      return repository;
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
      String key = ArtifactUtils.versionlessKey(artifact.getGroupId(), artifact.getArtifactId());
      if (projectsByGA.containsKey(key)) {
        return Arrays.asList(projectsByGA.get(key).getVersion());
      }
      return Collections.emptyList();
    }

    @Override
    public File findArtifact(Artifact artifact) {
      MavenProject project = projectsByGAV.get(ArtifactUtils.key(artifact.getGroupId(),
          artifact.getArtifactId(), artifact.getVersion()));
      if (project == null) {
        return null;
      }
      if (artifact.getExtension().equals("pom")) {
        return project.getFile();
      }
      return new File(project.getBuild().getOutputDirectory());
    }
  }

  @Test(expected = NoSuchElementException.class)
  public void testResourceNotFound() throws Exception {
    setup("transitive");
    resolver.getProjectDependency("g", "a", "c", ResolutionScope.COMPILE);
  }


  @Test
  public void testResolveAllCompileDependencies() throws Exception {
    setup("all-compile");
    Map<IArtifactMetadata, Path> resolved =
        resolver.getProjectDependencies(true, ResolutionScope.COMPILE);
    assertThat(resolved).containsValue(jarfile.toPath());
    assertThat(resolved).containsValue(dirfiledirect.toPath());
    assertThat(resolved).containsValue(dirfiletransitive.toPath());
  }

  @Test
  public void testResolveDirectDependencies() throws Exception {
    setup("all-compile");
    Map<IArtifactMetadata, Path> resolved =
        resolver.getProjectDependencies(false, ResolutionScope.COMPILE);
    assertThat(resolved).containsValue(jarfile.toPath());
    assertThat(resolved).containsValue(dirfiledirect.toPath());
    assertThat(resolved).doesNotContainValue(dirfiletransitive.toPath());
  }

  @Test
  public void testTestScopeNotResolved() throws Exception {
    setup("mixed-scope");
    Map<IArtifactMetadata, Path> resolved =
        resolver.getProjectDependencies(false, ResolutionScope.COMPILE);
    assertThat(resolved).doesNotContainValue(jarfile.toPath());
    assertThat(resolved).containsValue(dirfiledirect.toPath());
  }

  @Test
  public void testTestScopeResolvesCompileScope() throws Exception {
    setup("mixed-scope");
    Map<IArtifactMetadata, Path> resolved = resolver.getProjectDependencies(false, ResolutionScope.TEST);
    assertThat(resolved).containsValue(jarfile.toPath());
    assertThat(resolved).containsValue(dirfiledirect.toPath());
  }
}
