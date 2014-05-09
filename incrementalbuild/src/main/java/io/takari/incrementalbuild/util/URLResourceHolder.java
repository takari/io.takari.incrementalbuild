package io.takari.incrementalbuild.util;

import io.takari.incrementalbuild.BuildContext.ResourceStatus;
import io.takari.incrementalbuild.spi.ResourceHolder;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Allows tracking of input resources identified by URLs in BuildContext.
 * <p>
 * Resource contents SHA1 is used to determine if the resource has changed compared to the previous
 * build.
 * 
 * @experimental this class can be changed or removed without prior notice
 */
public class URLResourceHolder implements ResourceHolder<URL> {

  private final URL url;

  private final byte[] hash;

  public URLResourceHolder(URL url) throws IOException {
    this.url = url;
    this.hash = hash(url);
  }

  @Override
  public URL getResource() {
    return url;
  }

  @Override
  public ResourceStatus getStatus() {
    byte[] newHash;
    try {
      newHash = hash(url);
    } catch (IOException x) {
      return ResourceStatus.REMOVED;
    }
    return Arrays.equals(hash, newHash) ? ResourceStatus.UNMODIFIED : ResourceStatus.MODIFIED;
  }

  private static byte[] hash(URL url) throws IOException {
    // TODO figure out how to use Guava here
    MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
    InputStream is = new BufferedInputStream(url.openStream());
    try {
      int ch;
      while ((ch = is.read()) >= 0) {
        digest.update((byte) ch);
      }
    } finally {
      is.close();
    }
    return digest.digest();
  }

  @Override
  public int hashCode() {
    int hash = 31;
    hash = hash * 17 + url.hashCode();
    hash = hash * 17 + Arrays.hashCode(this.hash);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof URLResourceHolder)) {
      return false;
    }
    URLResourceHolder other = (URLResourceHolder) obj;
    return url.equals(other.url) && Arrays.equals(hash, other.hash);
  }
}
