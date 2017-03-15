package io.takari.builder.internal.model;


import java.io.File;
import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import io.takari.builder.*;

public class BuilderClass {
  private final TypeAdapter type;
  private final List<BuilderMethod> builders;
  private final List<AbstractParameter> parameters;

  public BuilderClass(TypeAdapter type, List<BuilderMethod> builders,
      List<AbstractParameter> parameters) {
    this.type = type;
    this.builders = Collections.unmodifiableList(builders);
    this.parameters = Collections.unmodifiableList(parameters);
  }

  public TypeAdapter type() {
    return type;
  }

  public List<AbstractParameter> parameters() {
    return parameters;
  }

  public List<BuilderMethod> builders() {
    return builders;
  }

  public void accept(BuilderMetadataVisitor visitor) {
    if (visitor.enterBuilderClass(this)) {
      parameters.forEach(p -> p.accept(visitor));
      builders.forEach(b -> b.accept(visitor));
      visitor.leaveBuilderClass(this);
    }
  }

  private static Collection<Class<? extends Annotation>> all_annotations;
  private static Collection<Class<? extends Annotation>> parameter_annotations;

  static {
    Collection<Class<? extends Annotation>> _parameters = new ArrayList<>();
    _parameters.add(Dependencies.class);
    _parameters.add(DependencyResources.class);
    _parameters.add(ArtifactResources.class);
    _parameters.add(GeneratedResourcesDirectory.class);
    _parameters.add(GeneratedSourcesDirectory.class);
    _parameters.add(InputDirectory.class);
    _parameters.add(InputDirectoryFiles.class);
    _parameters.add(InputFile.class);
    _parameters.add(OutputFile.class);
    _parameters.add(OutputDirectory.class);
    _parameters.add(Parameter.class);
    parameter_annotations = Collections.unmodifiableCollection(_parameters);

    Collection<Class<? extends Annotation>> _all = new ArrayList<>(_parameters);
    _all.add(Builder.class);
    all_annotations = Collections.unmodifiableCollection(_all);
  }

  /**
   * Returns all annotation classes supported by the model.
   */
  public static Collection<Class<? extends Annotation>> annotations() {
    return all_annotations;
  }

  /**
   * Returns all parameter annotation classes supported by the model.
   */
  public static Collection<Class<? extends Annotation>> parameterAnnotations() {
    return parameter_annotations;
  }

  //
  // construction
  //

  private static AbstractParameter createParameterMetadata(MemberAdapter field,
      TypeAdapter fieldType) {

    AbstractParameter node;
    if (fieldType.isArray() || fieldType.isIterable() && !fieldType.isSameType(Path.class)) {
      List<TypeAdapter> elementTypes = field.getParameterTypes();
      if (elementTypes.size() == 1
          && (!elementTypes.get(0).isIterable() || elementTypes.get(0).isSameType(Path.class))) {
        TypeAdapter elementType = elementTypes.get(0);
        if (field.isAnnotationPresent(DependencyResources.class)) {
          node = new DependencyResourcesParameter(field, elementType);
        } else if (field.isAnnotationPresent(ArtifactResources.class)) {
          node = new ArtifactResourcesParameter(field, elementType);
        } else if (field.isAnnotationPresent(InputDirectoryFiles.class)) {
          node = new InputDirectoryFilesParameter(field, elementType);
        } else if (field.isAnnotationPresent(Dependencies.class)) {
          node = new DependenciesParameter(field, elementType);
        } else {
          AbstractParameter members = createParameterMetadata(field, elementType);
          node = new MultivalueParameter(field, fieldType, members);
        }
      } else {
        node = new UnsupportedCollectionParameter(field, fieldType, elementTypes);
      }
    } else if (fieldType.isMap()) {
      if (field.isAnnotationPresent(Dependencies.class)) {
        List<TypeAdapter> elementTypes = field.getParameterTypes();
        if (elementTypes.size() == 2 && (elementTypes.get(1).isSameType(Path.class)
            || elementTypes.get(1).isSameType(File.class))) {
          node = new DependenciesParameter(field, fieldType, elementTypes.get(1));
        } else {
          node = new UnsupportedCollectionParameter(field, fieldType, elementTypes);
        }
      } else {
        node = new MapParameter(field, fieldType);
      }

    } else if (field.isAnnotationPresent(DependencyResources.class)) {
      node = new DependencyResourcesParameter(field, fieldType);
    } else if (field.isAnnotationPresent(ArtifactResources.class)) {
      node = new ArtifactResourcesParameter(field, fieldType);
    } else if (field.isAnnotationPresent(InputDirectoryFiles.class)) {
      node = new InputDirectoryFilesParameter(field, fieldType);
    } else if (field.isAnnotationPresent(InputDirectory.class)) {
      node = new InputDirectoryParameter(field, fieldType);
    } else if (field.isAnnotationPresent(OutputDirectory.class)) {
      node = new OutputDirectoryParameter(field, fieldType);
    } else if (field.isAnnotationPresent(OutputFile.class)) {
      node = new OutputFileParameter(field, fieldType);
    } else if (field.isAnnotationPresent(GeneratedResourcesDirectory.class)) {
      node = new GeneratedResourcesDirectoryParameter(field, fieldType);
    } else if (field.isAnnotationPresent(GeneratedSourcesDirectory.class)) {
      node = new GeneratedSourcesDirectoryParameter(field, fieldType);
    } else if (field.isAnnotationPresent(InputFile.class)) {
      node = new InputFileParameter(field, fieldType);
    } else {
      if (fieldType.isEnum() || SimpleParameter.isSimpleType(fieldType)) {
        node = new SimpleParameter(field, fieldType);
      } else {
        List<AbstractParameter> members = new ArrayList<>();
        fieldType.getAllMembers()
            .forEach(m -> members.add(createParameterMetadata(m, m.getType())));
        node = new CompositeParameter(field, fieldType, members);
      }
    }

    return node;
  }

  private static boolean isParameter(MemberAdapter field) {
    return parameter_annotations.stream().anyMatch(a -> field.isAnnotationPresent(a));
  }

  public static <E> BuilderClass create(TypeAdapter type) {
    List<BuilderMethod> builders = type.getMethods().stream() //
        .filter(m -> m.getAnnotation(Builder.class) != null) //
        .map(m -> new BuilderMethod(m, type)) //
        .collect(Collectors.toList());
    List<AbstractParameter> parameters = type.getAllMembers().stream() //
        .filter(m -> isParameter(m)) //
        .map(m -> createParameterMetadata(m, m.getType())) //
        .collect(Collectors.toList());
    return new BuilderClass(type, builders, parameters);
  }

}
