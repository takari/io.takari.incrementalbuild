package test.plugin;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;

@Mojo( name = "copyfile-nonincremental", defaultPhase = LifecyclePhase.COMPILE )
public class CopyFileNonIncrementalMojo
    extends AbstractMojo
{
    @Parameter
    private File input;

    @Parameter
    private File output;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        try
        {
            FileUtils.copyFile( input, input );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Could not copy file", e );
        }
    }
}
