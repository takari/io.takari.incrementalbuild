package io.takari.incremental.internal.maven;

import io.takari.incremental.internal.DefaultBuildContext;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.scope.MojoExecutionScoped;
import org.apache.maven.execution.scope.WeakMojoExecutionListener;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Maven specific BuildContext implementation that provides
 * <ul>
 * <li>Conventional location of incremental build state under ${build.build.directory}/incremental.
 * In the future, this may become configurable via well-known project property.</li>
 * <li>Automatic detection of configuration changes based on
 * <ul>
 * <li>Maven plugin artifacts GAVs, file sizes and timestamps</li>
 * <li>Project effective pom.xml. In the future, this may be narrowed down.</li>
 * <li>Maven session execution, i.e. user and system, properties.</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * @TODO decide how to handle volatile properties like ${maven.build.timestamp}. Should we always
 *       ignore them? Are there cases where output has to be always regenerated just to include new
 *       build timestamp, for example?
 */
@Named
@MojoExecutionScoped
public class MavenBuildContext extends DefaultBuildContext implements WeakMojoExecutionListener {

  @Inject
  public MavenBuildContext(MojoConfigurationDigester digester) {
    super(null, digester.digest());
  }

  @Override
  public void beforeMojoExecution(MojoExecutionEvent event) throws MojoExecutionException {
    // TODO Auto-generated method stub

  }

  @Override
  public void afterMojoExecutionSuccess(MojoExecutionEvent event) throws MojoExecutionException {
    // TODO Auto-generated method stub

  }

  @Override
  public void afterExecutionFailure(MojoExecutionEvent event) {
    // TODO Auto-generated method stub

  }


}
