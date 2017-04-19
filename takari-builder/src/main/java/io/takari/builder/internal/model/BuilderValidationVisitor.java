package io.takari.builder.internal.model;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.IArtifactResources;
import io.takari.builder.IDirectoryFiles;
import io.takari.builder.Parameter;
import io.takari.builder.ResolutionScope;

public abstract class BuilderValidationVisitor implements BuilderMetadataVisitor {

  static boolean isEmpty(String[] value) {
    return value == null || value.length == 0;
  }

  protected abstract void error(BuilderMethod builder, String message);

  protected abstract void error(AbstractParameter parameter, String message);

  private void error(AbstractParameter parameter, String message, Object... values) {
    error(parameter, String.format(message, values));
  }

  private ResolutionScope resolutionScope;

  private void validateParameterAnnotation(AbstractParameter parameter) {
    MemberAdapter element = parameter.originatingElement();
    Annotation annotation = parameter.annotation();
    Class<? extends Annotation> annotationClass =
        annotation != null ? annotation.annotationType() : null;

    assert annotationClass == null || element.isAnnotationPresent(annotationClass);

    TreeSet<String> presentAnnotations = BuilderClass.parameterAnnotations().stream() //
        .filter(a -> element.isAnnotationPresent(a)) //
        .map(a -> a.getSimpleName()) //
        .collect(Collectors.toCollection(TreeSet::new));

    if (presentAnnotations.size() > 1) {
      error(parameter, "ambigous parameter annotation present: %s", presentAnnotations);
    }
  }

  //
  //
  //

  @Override
  public boolean enterBuilderClass(BuilderClass metadata) {
    // validate parameter names are unique
    Map<String, AbstractParameter> parameters = new HashMap<>();
    for (AbstractParameter parameter : metadata.parameters()) {
      if (parameters.containsKey(parameter.name())) {
        AbstractParameter origin = parameters.get(parameter.name());
        error(parameter, "Builder parameter '%s' duplicates parameter defined in %s",
            parameter.name(), origin.originatingElement().getDeclaringType().qualifiedName());
      }
      parameters.put(parameter.name(), parameter);
    }

    return true;
  }

  @Override
  public void visitBuilder(BuilderMethod metadata) {
    // validate goal name
    String name = metadata.annotation().name();
    if (!name.matches("[\\p{Alnum}-]+")) {
      error(metadata, "invalid goal name");
    }

    // validate type is concrete
    TypeAdapter type = metadata.declaringType();
    if (type.isInterface() || type.isLocalClass() || type.isAnonymousClass() || type.isInnerClass()
        || type.isAbstract()) {
      error(metadata, "Only concrete classes are allowed to contain Builder methods");
    }

    // TODO validate type has no-arg constructor

    // validate method does not have parameters
    if (metadata.originatingElement().getParameterCount() > 0) {
      error(metadata, "Buidler method must not take parameters");
    }
  }

  @Override
  public boolean enterMultivalue(MultivalueParameter metadata) {
    // validate target type, see BuildInputsBulder for runtime implementation counterpart
    TypeAdapter type = metadata.type();
    if (!type.isArray()) {
      if (!type.isInterface() && !type.hasNoargConstructor()) {
        error(metadata, "multivalue prarmeter type %s must have no-arg constructor",
            type.qualifiedName());
      }
      if (type.isInterface() //
          && !type.isAssignableFrom(List.class) //
          && !type.isAssignableFrom(Set.class) //
          && !type.isAssignableFrom(Collection.class)) {
        error(metadata,
            "multivalue prarmeter type must be concrete type or one of Collection, List and Set");
      }
    }

    return true;
  }

  @Override
  public void visitUnsupportedCollection(UnsupportedCollectionParameter metadata) {
    List<TypeAdapter> elementTypes = metadata.elementTypes;
    if (elementTypes.isEmpty()) {
      error(metadata, "Raw Collection or wildcard Collection element type");
    } else {
      error(metadata, "Unsupported Collection element type %s", elementTypes.get(0).simpleName());
    }
  }

  @Override
  public void visitMap(MapParameter metadata) {
    TypeAdapter type = metadata.type();
    if (!type.isMap()) {
      throw new IllegalArgumentException(); // this is a bug in our code
    }

    List<TypeAdapter> types = metadata.element.getParameterTypes();
    if (types.size() != 2) {
      error(metadata, "Raw Map or wildcard Map key and/or value types");
      return;
    }

    if (!types.get(0).isSameType(String.class)) {
      error(metadata, "Key for map parameter must be assignable from String");
    }
    if (!SimpleParameter.isSimpleType(types.get(1))) {
      error(metadata, "Only simple parameters are allowed as values for map parameters");
    }
  }

  @Override
  public boolean enterComposite(CompositeParameter metadata) {
    // TODO validate composite member names are unique
    // TODO validate executable type (concrete, has no-arg constructor)

    Parameter ann = metadata.annotation();
    if (ann != null && (ann.value().length > 0 || ann.defaultValue().length > 0)) {
      error(metadata, "@Parameter target type %s does not support value/defaultValue",
          metadata.type().qualifiedName());
    }

    return true;
  }

  @Override
  public void visitSimple(SimpleParameter metadata) {
    validateParameterAnnotation(metadata);

    // TODO parameter target type must be supported
    // TODO if target type is enum, validate value/defaultValue (skip if use ${properties})

    // only one of value and defaultValue can be specified
    if (!isEmpty(metadata.value()) && !isEmpty(metadata.defaultValue())) {
      error(metadata, "@Parameter 'value' and 'defaultValue' attributes cannot be both specified");
    }

    // TODO if either value or defaultValue is specified, required must be true

    // if parameter target type is primitive, required must be true
    if (metadata.type().isPrimitive() && metadata.annotation() != null && !metadata.required()) {
      error(metadata, "Parameter '%s' of primitive type '%s' must be required", metadata.name(),
          metadata.type().qualifiedName());
    }

    // TODO validate value/defaultValue cardinality
  }

  @Override
  public void visitInputDirectory(InputDirectoryParameter metadata) {
    validateParameterAnnotation(metadata);

    // parameter target type
    TypeAdapter type = metadata.type();
    if (!type.isSameType(File.class) && !type.isSameType(Path.class)) {
      error(metadata, "@InputDirectory paramerer must be of type File or Path");
    }

    // only one of value and defaultValue can be specified
    if (!isEmpty(metadata.value()) && !isEmpty(metadata.defaultValue())) {
      error(metadata,
          "@InputDirectory 'value' and 'defaultValue' attributes cannot be both specified");
    }

    // TODO if either value or defaultValue is specified, required must be true
    // TODO validate value/defaultValue cardinality
  }

  @Override
  public void visitInputDirectoryFiles(InputDirectoryFilesParameter metadata) {
    validateParameterAnnotation(metadata);

    // parameter target type
    TypeAdapter type = metadata.type();
    if (type.isArray() || type.isIterable()) {
      type = metadata.originatingElement().getParameterTypes().get(0);
    }
    if (!type.isSameType(IDirectoryFiles.class) && !type.isSameType(File.class)
        && !type.isSameType(Path.class)) {
      error(metadata,
          "@InputDirectoryFiles paramerer must be of type DirectoryFiles, File, or Path");
    }

    // only one of value and defaultValue can be specified
    if (!isEmpty(metadata.value()) && !isEmpty(metadata.defaultValue())) {
      error(metadata,
          "@InputDirectoryFiles 'value' and 'defaultValue' attributes cannot be both specified");
    }
  }

  @Override
  public void visitDependencies(DependenciesParameter metadata) {
    TypeAdapter type = metadata.type();
    if (type.isMap()) {
      List<TypeAdapter> types = metadata.element.getParameterTypes();
      if (types.size() != 2) {
        error(metadata, "Raw Map or wildcard Map key and/or value types");
        return;
      }

      if (!types.get(0).isSameType(IArtifactMetadata.class)) {
        error(metadata, "Key for @Dependency map parameter must be of type IArtifactMetadata");
      }
      if (!types.get(1).isSameType(File.class) && !types.get(1).isSameType(Path.class)) {
        error(metadata, "Only Files or Paths are allowed as values for @Dependency map parameters");
      }
    } else {
      if (!metadata.type().isSameType(File.class)
          && !metadata.type().isSameType(IArtifactMetadata.class)
          && !metadata.type().isSameType(Path.class)) {
        error(metadata, "@Dependencies must be of type %s, %s or %s", File.class, Path.class,
            IArtifactMetadata.class);
      }
    }
    validateResolutionScope(metadata.annotation().scope(), metadata);
  }

  private void validateResolutionScope(ResolutionScope scope, AbstractParameter metadata) {
    if (resolutionScope != null && !resolutionScope.equals(scope)) {
      error(metadata, "ambiguous resolution scope configuration");
    } else {
      resolutionScope = scope;
    }
  }

  private void validateArtifactResources(AbstractResourceSelectionParameter metadata,
      ResolutionScope scope, String parameterName) {
    validateParameterAnnotation(metadata);
    validateResolutionScope(scope, metadata);

    // parameter target type
    TypeAdapter type = metadata.type();
    if (type.isArray() || type.isIterable()) {
      type = metadata.originatingElement().getParameterTypes().get(0);
    }
    if (!type.isSameType(URL.class) && !type.isSameType(IArtifactResources.class)) {
      error(metadata, parameterName + " parameter must be of type URL or IArtifactResources");
    }
  }

  @Override
  public void visitOutputDirectory(OutputDirectoryParameter metadata) {
    validateParameterAnnotation(metadata);
  }

  @Override
  public void visitOutputFile(OutputFileParameter metadata) {
    validateParameterAnnotation(metadata);
  }

  @Override
  public void visitGeneratedResourcesDirectory(GeneratedResourcesDirectoryParameter metadata) {
    validateParameterAnnotation(metadata);
  }

  @Override
  public void visitGeneratedSourcesDirectory(GeneratedSourcesDirectoryParameter metadata) {
    validateParameterAnnotation(metadata);
  }

  @Override
  public void visitInputFile(InputFileParameter metadata) {
    validateParameterAnnotation(metadata);
  }

  @Override
  public void visitDependencyResources(DependencyResourcesParameter metadata) {
    validateArtifactResources(metadata, metadata.annotation().scope(), "@DependencyResources");
  }

  @Override
  public void visitArtifactResources(ArtifactResourcesParameter metadata) {
    validateArtifactResources(metadata, metadata.annotation().scope(), "@ArtifactResources");
  }
}
