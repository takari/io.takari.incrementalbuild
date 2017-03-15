package io.takari.incrementalbuild.maven.testing;

import io.takari.incrementalbuild.maven.internal.MavenBuildContextFinalizer;
import io.takari.incrementalbuild.spi.AbstractBuildContext;
import io.takari.incrementalbuild.spi.DefaultBuildContextState;
import io.takari.incrementalbuild.spi.Message;

import java.io.File;
import java.util.Collection;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.plugin.MojoExecutionException;


@Named
@MojoExecutionScoped
class TestBuildContextFinalizer extends MavenBuildContextFinalizer {

  private final IncrementalBuildLog log;

  @Inject
  public TestBuildContextFinalizer(IncrementalBuildLog log) {
    this.log = log;
  }

  @Override
  public void afterMojoExecutionSuccess(MojoExecutionEvent event) throws MojoExecutionException {
    try {
      super.afterMojoExecutionSuccess(event);
    } finally {
      for (AbstractBuildContext context : getRegisteredContexts()) {
        DefaultBuildContextState state = context.getState();

        // carried over outputs
        for (File output : state.getOutputs()) {
          // if not processed during this build it must have been carried over
          if (!log.getRegisteredOutputs().contains(output)) {
            log.addCarryoverOutput(output);
          }
        }

        // messages
        for (Map.Entry<Object, Collection<Message>> entry : state.getResourceMessages()
            .entrySet()) {
          for (Message message : entry.getValue()) {
            log.message(entry.getKey(), message.line, message.column, message.message,
                toMessageSinkSeverity(message.severity), message.cause);
          }
        }
      }
    }
  }
}
