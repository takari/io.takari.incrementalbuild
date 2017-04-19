package io.takari.builder.apt;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import io.takari.builder.internal.model.BuilderClass;
import io.takari.builder.internal.model.MemberAdapter;
import io.takari.builder.internal.model.MethodAdapter;
import io.takari.builder.internal.model.TypeAdapter;

class APT {
  private final Elements elements;
  private final Types types;

  public APT(Elements elements, Types types) {
    this.elements = elements;
    this.types = types;
  }

  public class APTMethod implements MethodAdapter {
    private final ExecutableElement adaptee;

    public APTMethod(ExecutableElement adaptee) {
      this.adaptee = adaptee;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
      return adaptee.getAnnotation(annotationClass);
    }

    @Override
    public int getParameterCount() {
      return adaptee.getParameters().size();
    }

    public Element adaptee() {
      return adaptee;
    }
  }

  public class APTMember implements MemberAdapter {

    private final Element adaptee;

    public APTMember(Element adaptee) {
      this.adaptee = adaptee;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
      return adaptee.getAnnotation(annotationClass) != null;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
      return adaptee.getAnnotation(annotationClass);
    }

    public Element adaptee() {
      return adaptee;
    }

    @Override
    public String getName() {
      return adaptee.getSimpleName().toString();
    }

    @Override
    public APTType getDeclaringType() {
      return new APTType(adaptee.getEnclosingElement().asType());
    }

    @Override
    public List<TypeAdapter> getParameterTypes() {
      List<TypeMirror> parameterTypes = new ArrayList<>();
      TypeMirror type = adaptee.asType();

      if (type instanceof ArrayType) {
        parameterTypes.add(((ArrayType) type).getComponentType());
      } else if (type instanceof DeclaredType) {
        parameterTypes.addAll(((DeclaredType) type).getTypeArguments());
      }

      return parameterTypes.stream() //
          .filter(t -> t.getKind().isPrimitive() || t.getKind() == TypeKind.DECLARED) //
          .map(m -> new APTType(m)).collect(Collectors.toList());
    }

    @Override
    public APTType getType() {
      return new APTType(adaptee.asType());
    }

    public boolean isDeprecated() {
      return adaptee.getAnnotation(Deprecated.class) != null;
    }

    public String getJavadoc() {
      return elements.getDocComment(adaptee);
    }

    @Override
    public String toString() {
      return adaptee.toString();
    }
  }


  public class APTType implements TypeAdapter {

    private final TypeMirror adaptee;

    public APTType(TypeMirror adaptee) {
      assert adaptee instanceof PrimitiveType || adaptee instanceof DeclaredType
          || adaptee instanceof ArrayType;
      this.adaptee = adaptee;
    }

    public TypeMirror adaptee() {
      return adaptee;
    }

    @Override
    public String qualifiedName() {
      if (isPrimitive()) {
        // [...] the string should be of a form suitable for representing this type in source code
        return adaptee.toString();
      }
      return asTypeElement().getQualifiedName().toString();
    }

    @Override
    public String simpleName() {
      if (isPrimitive()) {
        return adaptee.getKind().toString();
      }
      return asTypeElement().getSimpleName().toString();
    }

    PackageElement getPackageElement() {
      if (isPrimitive()) {
        throw new UnsupportedOperationException();
      }
      return elements.getPackageOf(asTypeElement());
    }

    @Override
    public boolean isPrimitive() {
      return adaptee instanceof PrimitiveType;
    }

    @Override
    public boolean isIterable() {
      return types.isAssignable(types.erasure(adaptee), asTypeMirrorErasure(Iterable.class));
    }

    @Override
    public boolean isMap() {
      return types.isAssignable(types.erasure(adaptee), asTypeMirrorErasure(Map.class));
    }

    @Override
    public boolean isEnum() {
      return types.isSubtype(types.erasure(adaptee), asTypeMirrorErasure(Enum.class));
    }

    @Override
    public boolean isSameType(Class<?> type) {
      return types.isSameType(types.erasure(adaptee), asTypeMirrorErasure(type));
    }

    private TypeElement asTypeElement() {
      return (TypeElement) ((DeclaredType) adaptee).asElement();
    }

    private boolean isDeclaredType() {
      return adaptee instanceof DeclaredType;
    }

    @Override
    public List<MemberAdapter> getAllMembers() {
      if (isPrimitive()) {
        return Collections.emptyList();
      }
      return APT.this.getAllMembers(asTypeElement());
    }

    @Override
    public List<MethodAdapter> getMethods() {
      if (isPrimitive()) {
        return Collections.emptyList();
      }
      return asTypeElement().getEnclosedElements().stream() //
          .filter(m -> m.getKind() == ElementKind.METHOD) //
          .map(m -> new APTMethod((ExecutableElement) m)) //
          .collect(Collectors.toList());
    }

    @Override
    public String toString() {
      return adaptee.toString();
    }

    @Override
    public boolean isInterface() {
      return isDeclaredType() && asTypeElement().getKind() == ElementKind.INTERFACE;
    }

    @Override
    public boolean isLocalClass() {
      return isDeclaredType() && asTypeElement().getNestingKind() == NestingKind.LOCAL;
    }

    @Override
    public boolean isAnonymousClass() {
      return isDeclaredType() && asTypeElement().getNestingKind() == NestingKind.ANONYMOUS;
    }

    @Override
    public boolean isInnerClass() {
      if (!isDeclaredType()) {
        return false;
      }
      TypeElement typeElement = asTypeElement();
      return typeElement.getNestingKind() == NestingKind.MEMBER
          && typeElement.getModifiers().contains(Modifier.STATIC);
    }

    @Override
    public boolean isAbstract() {
      return isDeclaredType() && asTypeElement().getModifiers().contains(Modifier.ABSTRACT);
    }

    @Override
    public boolean isArray() {
      return adaptee.getKind() == TypeKind.ARRAY;
    }

    @Override
    public boolean isAssignableFrom(Class<?> type) {
      return types.isAssignable(asTypeMirrorErasure(type), types.erasure(adaptee));
    }

    @Override
    public boolean hasNoargConstructor() {
      if (isPrimitive()) {
        return false;
      }
      TypeElement typeElement = asTypeElement();
      return ElementFilter.constructorsIn(typeElement.getEnclosedElements()).stream() //
          .anyMatch(m -> m.getParameters().isEmpty());
    }
  }

  TypeMirror asTypeMirrorErasure(Class<?> type) {
    return types.erasure(elements.getTypeElement(type.getCanonicalName()).asType());
  }

  List<MemberAdapter> getAllMembers(TypeElement type) {
    // Elements#getAllMembers does not appear to return inherited fields

    List<MemberAdapter> result = new ArrayList<>();
    TypeMirror superclass = type.getSuperclass();
    if (superclass instanceof NoType) {
      return Collections.emptyList();
    }
    result.addAll(getAllMembers((TypeElement) types.asElement(superclass)));
    type.getEnclosedElements().stream() //
        .filter(e -> e.getKind() == ElementKind.FIELD) //
        .map(e -> new APTMember(e)) //
        .forEach(m -> result.add(m));
    return result;
  }

  public BuilderClass createBuilderClass(TypeElement type) {
    return BuilderClass.create(new APTType(type.asType()));
  }

  APTType adapt(TypeMirror type) {
    return new APTType(type);
  }
}
