package io.takari.builder.internal.model;

import io.takari.builder.OutputFile;

public class OutputFileParameter extends AbstractFileParameter<OutputFile> {

  public OutputFileParameter(MemberAdapter element, TypeAdapter type) {
    super(element, type, OutputFile.class);
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
    return annotation.required();
  }

  @Override
  public void accept(BuilderMetadataVisitor visitor) {
    visitor.visitOutputFile(this);
  }
}
