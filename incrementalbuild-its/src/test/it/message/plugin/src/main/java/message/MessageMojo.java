package message;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.configuration.Configuration;;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.util.IOUtil;

@Mojo(name = "message", defaultPhase = LifecyclePhase.COMPILE)
public class MessageMojo extends AbstractMojo {
  @Component
  private BuildContext context;

  @Parameter
  private File input;

  public void execute() throws MojoExecutionException, MojoFailureException {
    BuildContext.Input<File> input = context.registerInput(this.input).process();
    input.addMessage(0, 0, "error message", BuildContext.SEVERITY_ERROR, null);
    input.addMessage(0, 0, "warning message", BuildContext.SEVERITY_WARNING, null);
  }
}
