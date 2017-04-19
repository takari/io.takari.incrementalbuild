package io.takari.builder.internal.model;

import io.takari.builder.OutputDirectory;

public class OutputDirectoryParameter extends AbstractFileParameter<OutputDirectory> {

  public OutputDirectoryParameter(MemberAdapter element, TypeAdapter type) {
    super(element, type, OutputDirectory.class);
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
    visitor.visitOutputDirectory(this);
  }
}
