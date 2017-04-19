package io.takari.builder.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import io.takari.builder.IArtifactMetadata;

/**
 * Handler for URLs that "wrap" artifact resources. The URL path observable by clients is relative
 * to the originating artifact root (jar file in most cases).
 */
class ArtifactResourceURLStreamHandler extends URLStreamHandler {

  public static final String PROTOCOL = "artifactresource";

  private final URL resource;

  class InputURLCollection extends URLConnection {
    public InputURLCollection(URL url) {
      super(url);
    }

    @Override
    public void connect() throws IOException {}

    @Override
    public InputStream getInputStream() throws IOException {
      return resource.openStream();
    }
  }

  ArtifactResourceURLStreamHandler(URL resource) {
    this.resource = resource;
  }

  @Override
  protected URLConnection openConnection(URL url) {
    return new InputURLCollection(url);
  }

  public static URL newURL(IArtifactMetadata artifact, String relpath, URL resource) {
    try {
      StringBuilder host = new StringBuilder();
      host.append(artifact.getGroupId());
      host.append(':').append(artifact.getArtifactId());
      host.append(':').append(artifact.getVersion());
      if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
        host.append(':').append(artifact.getClassifier());
      }
      return new URL(PROTOCOL, host.toString(), -1, relpath,
          new ArtifactResourceURLStreamHandler(resource));
    } catch (MalformedURLException e) {
      throw new UncheckedIOException(e);
    }
  }

}
