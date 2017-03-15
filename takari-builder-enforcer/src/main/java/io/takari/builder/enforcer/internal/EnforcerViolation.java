package io.takari.builder.enforcer.internal;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class EnforcerViolation implements Comparable<EnforcerViolation> {

  private final EnforcerViolationType violationType;
  private final String file;
  private final List<String> stackTrace;

  public EnforcerViolation(EnforcerViolationType violationType, String file) {
    this(violationType, file, getStack());
  }

  EnforcerViolation(EnforcerViolationType violationType, String file, List<String> stackTrace) {
    this.violationType = violationType;
    this.file = file;
    this.stackTrace = stackTrace;
  }

  public EnforcerViolationType getViolationType() {
    return violationType;
  }

  public String getFile() {
    return file;
  }

  public List<String> getStackTrace() {
    return stackTrace;
  }

  public String getType() {
    return violationType.getType();
  }

  public String getFormattedViolation() {
    return violationType.getType() + (isExec() ? " comand:" : " file:") + file;
  }

  private boolean isExec() {
    return EnforcerViolationType.EXECUTE.equals(violationType);
  }

  @Override
  public int compareTo(EnforcerViolation o) {
    return file.compareTo(o.getFile());
  }

  @Override
  public String toString() {
    return String.format("%s:%s:%s", violationType, file, stackTrace);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof EnforcerViolation)) {
      return false;
    }
    EnforcerViolation other = (EnforcerViolation) obj;
    // stack is not part of hashCode/equals
    return violationType.equals(other.violationType) && file.equals(other.file);
  }

  @Override
  public int hashCode() {
    int result = 31;
    result = result * 17 + violationType.hashCode();
    result = result * 17 + file.hashCode();
    // stack is not part of hashCode/equals
    return result;
  }

  static List<String> getStack() {
    StackTraceElement[] elements = Thread.currentThread().getStackTrace();
    List<String> output = new ArrayList<>(elements.length);
    for (StackTraceElement e : elements) {
      output.add(e.toString());
    }
    return output;
  }

  //
  //
  //

  public static interface Writer extends Closeable {
    public void write(EnforcerViolation violation);
  }

  public static Writer newWriter(Path outputFile) throws IOException {
    BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
    return new Writer() {
      @Override
      public synchronized void close() throws IOException {
        writer.close();
      }

      @Override
      public synchronized void write(EnforcerViolation violation) {
        try {
          writer.append(violation.getType()).append(' ').append(violation.getFile()).append('\n');
          for (String frame : violation.getStackTrace()) {
            writer.append(' ').append(frame).append('\n');
          }
          writer.flush();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    };
  }

  public static List<EnforcerViolation> readFrom(Path outputFile) throws IOException {
    List<EnforcerViolation> violations = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(outputFile)) {
      EnforcerViolationType type = null;
      String file = null;
      List<String> stack = null;
      String str;
      while ((str = reader.readLine()) != null) {
        if (str.charAt(0) != ' ') {
          if (type != null && file != null) {
            violations.add(new EnforcerViolation(type, file, stack));
          }
          type = EnforcerViolationType.fromType(str.substring(0, 1));
          file = str.substring(2);
          stack = new ArrayList<>();
        } else {
          assert type != null && file != null && stack != null;
          stack.add(str.substring(1));
        }
      }
      if (type != null && file != null) {
        violations.add(new EnforcerViolation(type, file, stack));
      }
    }
    return violations;
  }
}
