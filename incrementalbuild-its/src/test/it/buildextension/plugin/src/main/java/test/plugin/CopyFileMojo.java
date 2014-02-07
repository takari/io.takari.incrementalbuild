package test.plugin;

import io.takari.incrementalbuild.BuildContext;

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
import org.codehaus.plexus.util.IOUtil;

@Mojo(name = "copyfile", defaultPhase = LifecyclePhase.COMPILE)
public class CopyFileMojo extends AbstractMojo {
  @Component
  private BuildContext context;

  @Parameter
  private File input;

  @Parameter
  private File output;

  public void execute() throws MojoExecutionException, MojoFailureException {
    BuildContext.Input<File> input = context.registerInput(this.input).process();
    if (input != null) {
      try {
        InputStream is = new FileInputStream(input.getResource());
        try {
          OutputStream os = input.associateOutput(this.output).newOutputStream();
          try {
            IOUtil.copy(is, os);
          } finally {
            IOUtil.close(os);
          }
        } finally {
          IOUtil.close(is);
        }
      } catch (IOException e) {
        throw new MojoExecutionException("Could not copy file", e);
      }
    }
  }
}
