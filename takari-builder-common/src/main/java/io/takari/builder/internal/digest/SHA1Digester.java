package io.takari.builder.internal.digest;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA1Digester {

  public static MessageDigest newInstance() {
    try {
      return MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unsupported JVM", e);
    }
  }

  //
  // convenient helpers
  //

  private static final Charset UTF8 = Charset.forName("UTF-8");

  public static BytesHash digest(String string) {
    MessageDigest digest = newInstance();
    if (string != null) {
      digest.update(string.getBytes(UTF8));
    }
    return new BytesHash(digest.digest());
  }
}
