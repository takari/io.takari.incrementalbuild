package io.takari.builder.apt;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import org.apache.maven.plugins.annotations.*;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import com.squareup.javapoet.*;

import io.takari.builder.Builder;
import io.takari.builder.apt.APT.*;
import io.takari.builder.internal.maven.AbstractIncrementalMojo;
import io.takari.builder.internal.model.*;

public class MojoGenerationVisitor implements BuilderMetadataVisitor {
  private final Filer filer;
  private final Messager messager;

  // private TypeSpec.Builder classBuilder;
  private final List<FieldSpec> fields = new ArrayList<>();
  private final Set<TypeElement> types = new LinkedHashSet<>();

  private String resolutionScope = null;

  public MojoGenerationVisitor(Filer filer, Messager messager) {
    this.filer = filer;
    this.messager = messager;
  }

  private static MethodSpec buildConstructorSpec(TypeAdapter typeElement) {
    return MethodSpec.constructorBuilder() //
        .addModifiers(Modifier.PUBLIC) //
        .addStatement("super($T.class)", ((APTType) typeElement).adaptee()) //
        .build();
  }

  private String getMojoClassname(BuilderMethod metadata) {
    String classname = ((APTType) metadata.declaringType()).simpleName();
    String goalname = metadata.annotation().name().replace('-', '_');
    return classname + "$GeneratedMojo$" + goalname;
  }

  private String getMojoPackagename(BuilderMethod metadata) {
    APTType type = (APTType) metadata.declaringType();
    return type.getPackageElement().getQualifiedName().toString();
  }

  private void addField(AbstractParameter metadata) {
    APTMember element = (APTMember) metadata.originatingElement();
    FieldSpec.Builder builder = FieldSpec.builder(PlexusConfiguration.class, metadata.name());
    builder.addAnnotation(Parameter.class);
    if (element.isDeprecated()) {
      builder.addAnnotation(Deprecated.class);
    }
    String javadoc = element.getJavadoc();
    if (javadoc != null) {
      builder.addJavadoc(javadoc);
    }
    FieldSpec fieldSpec = builder.build();
    if(!fields.contains(fieldSpec)) {
      fields.add(fieldSpec);
      types.add((TypeElement) element.adaptee().getEnclosingElement());
    }
  }

  private void visitScopedMetadata(AbstractParameter metadata, io.takari.builder.ResolutionScope scope) {
    addField(metadata);
    checkResolutionScope(scope);
  }

  private void checkResolutionScope(io.takari.builder.ResolutionScope scope) {
    assert resolutionScope == null || resolutionScope.equals(scope.name());
    resolutionScope = scope.name();
  }

  //
  //
  //

  @Override
  public void visitBuilder(BuilderMethod metadata) {
    Builder ann = metadata.annotation();
    String defaultPhase = ann.defaultPhase().name();
    String resolutionScope =
        this.resolutionScope != null ? this.resolutionScope : ResolutionScope.NONE.name();

    AnnotationSpec.Builder annBuilder = AnnotationSpec.builder(Mojo.class) //
        .addMember("name", "$S", ann.name()) //
        .addMember("defaultPhase", "$T.$L", LifecyclePhase.class, defaultPhase) //
        .addMember("requiresProject", "$L", true) //
        .addMember("requiresDependencyResolution", "$T.$L", ResolutionScope.class, resolutionScope) //
        .addMember("threadSafe", "$L", true);

    TypeSpec.Builder classBuilder = TypeSpec.classBuilder(getMojoClassname(metadata)) //
        .superclass(AbstractIncrementalMojo.class) //
        .addModifiers(Modifier.PUBLIC) //
        .addAnnotation(annBuilder.build()) //
        .addFields(fields) //
        .addMethod(buildConstructorSpec(metadata.declaringType()));

    APTMethod element = (APTMethod) metadata.originatingElement();
    TypeElement type = (TypeElement) element.adaptee().getEnclosingElement();
    classBuilder.addOriginatingElement(type);
    
    if (metadata.isNonDeterministic()) {
      messager.printMessage(Kind.WARNING, "This Builder is declared as non-deterministic. This is BAD. Please Fix this asap.", type);
    }
    
    if (types.size() != 1 || !types.contains(type)) {
      // due to limitations of takari-lifecycle/m2e support and underlying deficiency of annotation
      // processing api, it is not possible to support incremental mojo generation when builder
      // implementation extends other classes. the workaround is to use proc=procEX on command line
      // and to explicitly invoke clean build inside eclipse. there is no way to detect which build
      // is currently running, so we always create an warning message.
      messager.printMessage(Kind.WARNING,
          "Builder extends other classes. Generated mojo may be incomplete. Make sure to run full build",
          type);
    }

    String pkgname = getMojoPackagename(metadata);

    try {
      JavaFile.builder(pkgname, classBuilder.build()) //
          .build().writeTo(filer);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public boolean enterMultivalue(MultivalueParameter metadata) {
    addField(metadata);

    return true;
  }

  @Override
  public void visitUnsupportedCollection(UnsupportedCollectionParameter metadata) {
    throw new IllegalArgumentException(); // should have been reported during validation
  }

  @Override
  public boolean enterComposite(CompositeParameter metadata) {
    addField(metadata);

    // ensure resolution scopes are honored within composites
    metadata.members.stream()
      .filter(m -> m instanceof DependencyResourcesParameter)
      .forEach(m -> {
        checkResolutionScope(((DependencyResourcesParameter) m).annotation().scope());
      });
    
    return false; // no need to generate anything for individual members
  }

  @Override
  public void visitMap(MapParameter metadata) {
    addField(metadata);
  }

  @Override
  public void visitSimple(SimpleParameter metadata) {
    addField(metadata);
  }

  @Override
  public void visitInputDirectory(InputDirectoryParameter metadata) {
    addField(metadata);
  }

  @Override
  public void visitInputDirectoryFiles(InputDirectoryFilesParameter metadata) {
    addField(metadata);
  }

  @Override
  public void visitDependencies(DependenciesParameter metadata) {
    visitScopedMetadata(metadata, metadata.annotation().scope());
  }

  @Override
  public void visitOutputDirectory(OutputDirectoryParameter metadata) {
    addField(metadata);
  }
  
  @Override
  public void visitOutputFile(OutputFileParameter metadata) {
    addField(metadata);
  }

  @Override
  public void visitGeneratedSourcesDirectory(GeneratedSourcesDirectoryParameter metadata) {
    addField(metadata);
  }

  @Override
  public void visitGeneratedResourcesDirectory(GeneratedResourcesDirectoryParameter metadata) {
    addField(metadata);
  }

  @Override
  public void visitInputFile(InputFileParameter metadata) {
    addField(metadata);
  }
  
  @Override
  public void visitDependencyResources(DependencyResourcesParameter metadata) {
    visitScopedMetadata(metadata, metadata.annotation().scope());
  }

  @Override
  public void visitArtifactResources(ArtifactResourcesParameter metadata) {
    visitScopedMetadata(metadata, metadata.annotation().scope());
  }
}
