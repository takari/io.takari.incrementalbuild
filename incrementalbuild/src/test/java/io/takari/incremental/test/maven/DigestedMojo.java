package io.takari.incremental.test.maven;

import io.takari.incrementalbuild.configuration.Configuration;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

@SuppressWarnings("unused")
public class DigestedMojo extends AbstractMojo {

  private String string;

  private MavenProject project;

  private File output;

  private String camelCase;

  @Configuration(ignored = true)
  private String ignored;

  @Configuration(ignored = false)
  private String notignored;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {}

}
