package io.takari.incremental.test.maven;

import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

@SuppressWarnings("unused")
public class DigestedMojo extends AbstractMojo {

  private String string;

  @Incremental(configuration = Configuration.consider)
  private MavenProject project;

  @Incremental(configuration = Configuration.consider)
  private MavenSession session;

  private MavenProject unannotatedProject;

  private List<MavenProject> unannotatedProjectList;

  private MavenSession unannotatedSession;

  private File output;

  private String camelCase;

  @Incremental(configuration = Configuration.ignore)
  private String ignored;

  @Incremental(configuration = Configuration.consider)
  private String notignored;

  private ArtifactRepository localrepo;

  private List<ArtifactRepository> remotes;

  private Set<Artifact> dependencies;

  private List<String> strings;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {}

}
