package io.takari.builder.internal.model;

import io.takari.builder.InputFile;

public class InputFileParameter extends AbstractFileParameter<InputFile> {

  public InputFileParameter(MemberAdapter element, TypeAdapter type) {
    super(element, type, InputFile.class);
  }

  @Override
  public String[] value() {
    return annotation.value();
  }

  @Override
  public String[] defaultValue() {
    return annotation.defaultValue();
  }

  @Override
  public boolean required() {
    return annotation.fileRequired();
  }

  @Override
  public void accept(BuilderMetadataVisitor visitor) {
    visitor.visitInputFile(this);
  }
}
