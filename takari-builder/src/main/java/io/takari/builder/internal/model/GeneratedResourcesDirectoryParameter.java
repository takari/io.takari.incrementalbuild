package io.takari.builder.internal.model;

import io.takari.builder.GeneratedResourcesDirectory;
import io.takari.builder.ResourceType;

public class GeneratedResourcesDirectoryParameter extends AbstractFileParameter<GeneratedResourcesDirectory> {

  public GeneratedResourcesDirectoryParameter(MemberAdapter element, TypeAdapter type) {
    super(element, type, GeneratedResourcesDirectory.class);
  }

  @Override
  public boolean required() {
    return annotation.required();
  }

  @Override
  public String[] value() {
    return annotation.value();
  }

  @Override
  public String[] defaultValue() {
    return annotation.defaultValue();
  }

  public ResourceType getResourceType() {
    return annotation.type();
  }
  public String[] includes() {
    return annotation.includes();
  }

  public String[] defaultIncludes() {
    return annotation.defaultIncludes();
  }

  public String[] excludes() {
    return annotation.excludes();
  }

  public String[] defaultExcludes() {
    return annotation.defaultExcludes();
  }


  @Override
  public void accept(BuilderMetadataVisitor visitor) {
    visitor.visitGeneratedResourcesDirectory(this);
  }
}
