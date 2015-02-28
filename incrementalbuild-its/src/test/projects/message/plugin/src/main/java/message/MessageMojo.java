package message;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.MessageSeverity;
import io.takari.incrementalbuild.Resource;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "message", defaultPhase = LifecyclePhase.COMPILE)
public class MessageMojo extends AbstractMojo {
  @Component
  private BuildContext context;

  @Parameter
  private File input;

  public void execute() throws MojoExecutionException, MojoFailureException {
    Resource<File> input = context.registerInput(this.input).process();
    input.addMessage(0, 0, "error message", MessageSeverity.ERROR, null);
    input.addMessage(0, 0, "warning message", MessageSeverity.WARNING, null);
  }
}
