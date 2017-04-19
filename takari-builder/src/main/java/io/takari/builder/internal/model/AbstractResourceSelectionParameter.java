package io.takari.builder.internal.model;

public abstract class AbstractResourceSelectionParameter extends AbstractParameter {

  protected AbstractResourceSelectionParameter(MemberAdapter element, TypeAdapter type) {
    super(element, type);
  }

  public abstract String[] value();

  public abstract String[] defaultValue();

  public abstract String[] includes();

  public abstract String[] defaultIncludes();

  public abstract String[] excludes();

  public abstract String[] defaultExcludes();

}
