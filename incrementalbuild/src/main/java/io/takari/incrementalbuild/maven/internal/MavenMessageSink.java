package io.takari.incrementalbuild.maven.internal;

import io.takari.incrementalbuild.spi.DefaultMessageSink;
import io.takari.incrementalbuild.workspace.MessageSink;

import javax.inject.Named;

import org.apache.maven.execution.scope.MojoExecutionScoped;

@Named
@MojoExecutionScoped
public class MavenMessageSink extends DefaultMessageSink implements MessageSink {

}
