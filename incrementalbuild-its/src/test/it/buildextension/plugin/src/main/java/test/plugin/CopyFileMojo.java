package test.plugin;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;

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

@Mojo(name = "copyfile", defaultPhase = LifecyclePhase.COMPILE)
public class CopyFileMojo extends AbstractMojo {
  @Component
  private BuildContext context;

  @Parameter
  private File input;

  @Parameter
  private File output;

  @Component
  @Incremental(configuration=Configuration.ignore)
  private MavenProject project;

  public void execute() throws MojoExecutionException, MojoFailureException {
    ClassRealm classRealm = project.getClassRealm();
    if (classRealm != null) {
      try {
        // make sure project realm and plugin realm agree on BuildContext.class
        // the condition is the same instance (i.e. ==) not equal objects
        Class<?> contextClass = context.getClass();
        if (contextClass != classRealm.loadClass(contextClass.getName())) {
          // NoClassDefFoundError triggers maven core to dump classrealm state
          throw new NoClassDefFoundError("Inconsistent MavenProject class realm");
        }
      } catch (ClassNotFoundException e) {
        throw new MojoExecutionException("Inconsistent MavenProject class realm", e);
      }
    }

    BuildContext.Input<File> input = context.registerInput(this.input).process();
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
