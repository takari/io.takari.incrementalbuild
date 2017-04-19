package io.takari.builder.enforcer.internal;

public enum EnforcerViolationType {
  READ("R"),
  WRITE("W"),
  EXECUTE("E");

  private final String type;

  private EnforcerViolationType(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  public static EnforcerViolationType fromType(String type) {
    for (EnforcerViolationType value : values()) {
      if (type.equals(value.getType())) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid enforcer violation type: " + type);
  }
}
