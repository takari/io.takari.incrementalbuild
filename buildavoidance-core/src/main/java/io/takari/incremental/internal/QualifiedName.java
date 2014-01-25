package io.takari.incremental.internal;

import java.io.Serializable;

class QualifiedName implements Serializable {
  private static final long serialVersionUID = 8966369370744414886L;
  private final String qualifier;
  private final String localName;

  public QualifiedName(String qualifier, String localName) {
    this.qualifier = qualifier;
    this.localName = localName;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = result * 31 + qualifier.hashCode();
    result = result * 31 + localName.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof QualifiedName)) {
      return false;
    }
    QualifiedName other = (QualifiedName) obj;
    return qualifier.equals(other.qualifier) && localName.equals(other.localName);
  }

  public String getQualifier() {
    return qualifier;
  }

  public String getLocalName() {
    return localName;
  }
}
