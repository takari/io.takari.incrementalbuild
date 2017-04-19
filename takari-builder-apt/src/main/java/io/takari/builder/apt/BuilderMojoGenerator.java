package io.takari.builder.apt;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import io.takari.builder.apt.APT.APTMember;
import io.takari.builder.apt.APT.APTMethod;
import io.takari.builder.internal.model.*;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BuilderMojoGenerator extends AbstractProcessor {

  private Filer filer;
  private Messager messager;

  private APT apt;

  private class APTValidationVisitor extends BuilderValidationVisitor {
    public boolean errorRaised;

    @Override
    protected void error(AbstractParameter parameter, String message) {
      APTMember element = (APTMember) parameter.originatingElement();
      error(element.adaptee(), message);
    }

    @Override
    protected void error(BuilderMethod builder, String message) {
      APTMethod builderMethod = (APTMethod) builder.originatingElement();
      error(builderMethod.adaptee(), message);
    }

    private void error(Element element, String message) {
      // TODO AnnotationMirror annotationMirror
      messager.printMessage(Kind.ERROR, message, element);
      errorRaised = true;
    }
  };

  @Override
  public synchronized void init(ProcessingEnvironment env) {
    super.init(env);

    this.filer = env.getFiler();
    this.messager = env.getMessager();
    this.apt = new APT(env.getElementUtils(), env.getTypeUtils());
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    Set<String> annotations = new LinkedHashSet<>();
    annotations.add("io.takari.builder.*");
    return annotations;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
    BuilderClass.annotations().stream() //
        .flatMap(a -> env.getElementsAnnotatedWith(a).stream()) //
        .map(m -> (TypeElement) m.getEnclosingElement()) //
        .distinct().forEach(m -> process(m, env));
    return false;
  }

  private void process(TypeElement type, RoundEnvironment env) {
    BuilderClass metadata = apt.createBuilderClass(type);
    APTValidationVisitor validator = new APTValidationVisitor();
    metadata.accept(validator);
    if (!validator.errorRaised) {
      metadata.accept(new MojoGenerationVisitor(filer, messager));
    }
  }
}
