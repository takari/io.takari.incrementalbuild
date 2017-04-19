package io.takari.builder;

import java.net.URL;
import java.util.Set;

/**
 * Encapsulates resources selected from an artifact
 */
public interface IArtifactResources {

  IArtifactMetadata artifact();

  /**
   * Selected resources.
   * 
   * <p>
   * URL#openStream returns InputStream of the resource contents. URL#getPath returns resource path
   * relative to artifact "base" (either zip file root or artifact directory basedir). Behaviour of
   * all other URL methods is undefined.
   */
  Set<URL> resources();
}
