package io.takari.incrementalbuild.maven.internal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class SHA1Digester {

  public static MessageDigest newInstance() {
    try {
      return MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unsupported JVM", e);
    }
  }
}
