package io.takari.incrementalbuild.maven.internal;

import java.io.Serializable;
import java.util.Arrays;

class BytesHash implements Serializable {

  private static final long serialVersionUID = 1L;

  private final byte[] bytes;

  public BytesHash(byte[] bytes) {
    this.bytes = bytes;
  }

  // TODO toString

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof BytesHash)) {
      return false;
    }
    return Arrays.equals(bytes, ((BytesHash) obj).bytes);
  }
}
