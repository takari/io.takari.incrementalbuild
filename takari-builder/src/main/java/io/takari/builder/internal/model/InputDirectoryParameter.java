package io.takari.builder.internal.model;

import io.takari.builder.InputDirectory;

public class InputDirectoryParameter extends AbstractParameter {

  private final InputDirectory annotation;

  InputDirectoryParameter(MemberAdapter element, TypeAdapter type) {
    super(element, type);
    this.annotation = element.getAnnotation(InputDirectory.class);
  }

  @Override
  public InputDirectory annotation() {
    return annotation;
  }

  @Override
  public boolean required() {
    return annotation.filesRequired();
  }

  public String[] value() {
    return annotation.value();
  }

  public String[] defaultValue() {
    return annotation.defaultValue();
  }

  public String[] includes() {
    return annotation.includes();
  }

  public String[] excludes() {
    return annotation.excludes();
  }

  @Override
  public void accept(BuilderMetadataVisitor visitor) {
    visitor.visitInputDirectory(this);
  }

}
