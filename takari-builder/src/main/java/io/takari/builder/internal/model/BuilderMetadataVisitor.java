package io.takari.builder.internal.model;

public interface BuilderMetadataVisitor {

  default boolean enterBuilderClass(BuilderClass metadata) {
    return true;
  }

  default void leaveBuilderClass(BuilderClass metadata) {}

  default boolean enterMultivalue(MultivalueParameter metadata) {
    return true;
  }

  default void leaveMultivalue(MultivalueParameter metadata) {}

  void visitMap(MapParameter metadata);

  void visitSimple(SimpleParameter metadata);

  default boolean enterComposite(CompositeParameter metadata) {
    return true;
  }

  default void leaveComposite(CompositeParameter metadata) {}

  void visitBuilder(BuilderMethod metadata);

  void visitInputDirectory(InputDirectoryParameter metadata);

  void visitInputDirectoryFiles(InputDirectoryFilesParameter metadata);

  void visitDependencies(DependenciesParameter metadata);

  void visitOutputDirectory(OutputDirectoryParameter metadata);

  void visitOutputFile(OutputFileParameter metadata);

  void visitGeneratedResourcesDirectory(GeneratedResourcesDirectoryParameter metadata);

  void visitGeneratedSourcesDirectory(GeneratedSourcesDirectoryParameter metadata);

  void visitInputFile(InputFileParameter metadata);

  void visitDependencyResources(DependencyResourcesParameter metadata);

  void visitArtifactResources(ArtifactResourcesParameter metadata);

  void visitUnsupportedCollection(UnsupportedCollectionParameter metadata);

}
