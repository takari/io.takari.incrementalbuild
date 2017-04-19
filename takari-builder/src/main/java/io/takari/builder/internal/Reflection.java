package io.takari.builder.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.takari.builder.internal.model.BuilderClass;
import io.takari.builder.internal.model.MemberAdapter;
import io.takari.builder.internal.model.MethodAdapter;
import io.takari.builder.internal.model.TypeAdapter;

public class Reflection {

  /** returns all fields of the class, including inherited fields */
  public static Collection<Field> getAllFields(Class<?> clazz) {
    return addFields(clazz, new ArrayList<>());
  }

  private static List<Field> addFields(Class<?> clazz, List<Field> fields) {
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass(), fields);
    }
    for (Field field : clazz.getDeclaredFields()) {
      boolean funny = field.isSynthetic() // TODO test
          || Modifier.isStatic(field.getModifiers());
      if (!funny) {
        fields.add(field);
      }
    }
    return fields;
  }

  public static boolean isIterableType(Class<?> fieldType) {
    return Iterable.class.isAssignableFrom(fieldType);
  }

  public static boolean isMapType(Class<?> fieldType) {
    return Map.class.isAssignableFrom(fieldType);
  }

  public static List<Class<?>> getParameterTypes(Field field) {
    List<Class<?>> types = new ArrayList<>();
    Class<?> fieldType = field.getType();
    if (fieldType.isArray()) {
      types.add(fieldType.getComponentType());
    } else if (isIterableType(fieldType) || isMapType(fieldType)) {
      if (field.getGenericType() instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        for (Type typeArgument : typeArguments) {
          if (typeArgument instanceof Class<?>) {
            types.add((Class<?>) typeArgument);
          }
          if (typeArgument instanceof ParameterizedType) {
            types.add((Class<?>) ((ParameterizedType) typeArguments[0]).getRawType());
          }
        }
      }
    }
    return types;
  }

  public static class ReflectionMethod implements MethodAdapter {
    private final Method adaptee;

    public ReflectionMethod(Method adaptee) {
      this.adaptee = adaptee;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
      return adaptee.getAnnotation(annotationClass);
    }

    @Override
    public int getParameterCount() {
      return adaptee.getParameterCount();
    }

    public Method adaptee() {
      return adaptee;
    }

  }

  public static class ReflectionField implements MemberAdapter {

    private final Field adaptee;

    public ReflectionField(Field adaptee) {
      this.adaptee = adaptee;
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
      return adaptee.isAnnotationPresent(annotationClass);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
      return adaptee.getAnnotation(annotationClass);
    }

    public Field adaptee() {
      return adaptee;
    }

    @Override
    public String getName() {
      return adaptee.getName();
    }

    @Override
    public ReflectionType getDeclaringType() {
      return new ReflectionType(adaptee.getDeclaringClass());
    }

    @Override
    public List<TypeAdapter> getParameterTypes() {
      return Reflection.getParameterTypes(adaptee).stream()
          .map(type -> new ReflectionType(type))
          .collect(Collectors.toList());
    }

    @Override
    public ReflectionType getType() {
      return new ReflectionType(adaptee.getType());
    }

    @Override
    public String toString() {
      return adaptee.toString();
    }
  }

  public static class ReflectionType implements TypeAdapter {

    private final Class<?> adaptee;

    public ReflectionType(Class<?> adaptee) {
      this.adaptee = adaptee;
    }

    @Override
    public String simpleName() {
      return adaptee.getSimpleName();
    }

    @Override
    public String qualifiedName() {
      return adaptee.getCanonicalName();
    }

    public Class<?> adaptee() {
      return adaptee;
    }

    @Override
    public boolean isPrimitive() {
      return adaptee.isPrimitive();
    }

    @Override
    public boolean isIterable() {
      return Reflection.isIterableType(adaptee);
    }

    @Override
    public boolean isMap() {
      return Reflection.isMapType(adaptee);
    }

    @Override
    public boolean isEnum() {
      return adaptee.isEnum();
    }

    @Override
    public List<MemberAdapter> getAllMembers() {
      List<MemberAdapter> members = new ArrayList<>();
      for (Field field : Reflection.getAllFields(adaptee)) {
        members.add(new ReflectionField(field));
      }
      return members;
    }

    @Override
    public List<MethodAdapter> getMethods() {
      List<MethodAdapter> methods = new ArrayList<>();
      for (Method method : adaptee.getDeclaredMethods()) {
        methods.add(new ReflectionMethod(method));
      }
      return methods;
    }

    @Override
    public boolean isSameType(Class<?> type) {
      return adaptee == type;
    }

    @Override
    public String toString() {
      return adaptee.toString();
    }

    @Override
    public boolean isInterface() {
      return adaptee.isInterface();
    }

    @Override
    public boolean isLocalClass() {
      return adaptee.isLocalClass();
    }

    @Override
    public boolean isAnonymousClass() {
      return adaptee.isAnonymousClass();
    }

    @Override
    public boolean isInnerClass() {
      return adaptee.getEnclosingClass() != null && !Modifier.isStatic(adaptee.getModifiers());
    }

    @Override
    public boolean isAbstract() {
      return Modifier.isAbstract(adaptee.getModifiers());
    }

    @Override
    public boolean isArray() {
      return adaptee.isArray();
    }

    @Override
    public boolean isAssignableFrom(Class<?> type) {
      return adaptee.isAssignableFrom(type);
    }

    @Override
    public boolean hasNoargConstructor() {
      try {
        return adaptee.getConstructor() != null;
      } catch (NoSuchMethodException | SecurityException e) {
        return false;
      }
    }

    public MultivalueFactory multivalueFactory() {
      MultivalueFactory factory;
      if (isIterable()) {
        if (!adaptee.isInterface()) {
          Constructor<Collection<Object>> constructor = getConstructor(adaptee);
          factory = (elements) -> {
            Collection<Object> collection = constructor.newInstance();
            collection.addAll(elements);
            return collection;
          };
        } else if (List.class.isAssignableFrom(adaptee)) {
          factory = (elements) -> Collections.unmodifiableList(new ArrayList<>(elements));
        } else if (Set.class.isAssignableFrom(adaptee)) {
          factory = (elements) -> Collections.unmodifiableSet(new LinkedHashSet<>(elements));
        } else if (Collection.class.isAssignableFrom(adaptee)) {
          factory = (elements) -> Collections.unmodifiableList(new ArrayList<>(elements));
        } else {
          throw new IllegalArgumentException(); // should have been caught by validation
        }
      } else if (isArray()) {
        factory = (elements) -> {
          Object array = Array.newInstance(adaptee.getComponentType(), elements.size());
          Iterator<?> iter = elements.iterator();
          for (int i = 0; iter.hasNext(); i++) {
            Array.set(array, i, iter.next());
          }
          return array;
        };
      } else {
        throw new UnsupportedOperationException("not a multi-value type:" + adaptee);
      }

      return factory;
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> Constructor<T> getConstructor(Class<?> type) {
    try {
      return (Constructor<T>) type.getConstructor();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(); // should have been caught by validation
    }
  }

  public static BuilderClass createBuilderClass(Class<?> clazz) {
    return BuilderClass.create(new ReflectionType(clazz));
  }

  /** Array or Collection factory */
  static interface MultivalueFactory {
    Object newInstance(Collection<?> elements) throws ReflectiveOperationException;
  }
}
