package io.takari.builder.internal.model;

import io.takari.builder.ArtifactResources;

public class ArtifactResourcesParameter extends AbstractResourceSelectionParameter {

  private static final String[] EMPTY = new String[0];

  private final ArtifactResources annotation;

  public ArtifactResourcesParameter(MemberAdapter element, TypeAdapter type) {
    super(element, type);
    this.annotation = element.getAnnotation(ArtifactResources.class);
  }

  @Override
  public ArtifactResources annotation() {
    return annotation;
  }

  @Override
  public boolean required() {
    return annotation.resourcesRequired();
  }

  @Override
  public void accept(BuilderMetadataVisitor visitor) {
    visitor.visitArtifactResources(this);
  }

  @Override
  public String[] value() {
    return EMPTY;
  }

  @Override
  public String[] defaultValue() {
    return EMPTY;
  }

  @Override
  public String[] includes() {
    return annotation.includes();
  }

  @Override
  public String[] defaultIncludes() {
    return annotation.defaultIncludes();
  }

  @Override
  public String[] excludes() {
    return annotation.excludes();
  }

  @Override
  public String[] defaultExcludes() {
    return annotation.defaultExcludes();
  }

}
