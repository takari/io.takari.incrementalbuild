package io.takari.incrementalbuild.maven.internal;

import io.takari.incrementalbuild.configuration.Configuration;

import java.io.Serializable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

class Digesters {

  // dies with class not found error if UTF-8 charset is not present
  static final Charset UTF_8 = Charset.forName("UTF-8");

  static interface Digester<T> {
    Serializable digest(T value);
  }

  private static final Map<Class<?>, Digester<?>> DIGESTERS;


  private static final Digester<Serializable> DIGESTER_ECHO = new Digester<Serializable>() {
    @Override
    public Serializable digest(Serializable value) {
      return value;
    }
  };

  static {
    Map<Class<?>, Digester<?>> digesters = new LinkedHashMap<Class<?>, Digester<?>>();
    digesters.put(Serializable.class, DIGESTER_ECHO);
    DIGESTERS = Collections.unmodifiableMap(digesters);
  }

  public static Serializable digest(Member member, Object value) {
    if (member instanceof AnnotatedElement) {
      Configuration configuration = ((AnnotatedElement) member).getAnnotation(Configuration.class);
      if (configuration != null && configuration.ignored()) {
        return null; // no digest, ignore
      }
    }

    Digester<?> digester = null;
    for (Map.Entry<Class<?>, Digester<?>> entry : DIGESTERS.entrySet()) {
      if (entry.getKey().isInstance(value)) {
        digester = entry.getValue();
        break;
      }
    }
    if (digester == null) {
      // TODO more informative exception message?
      throw new IllegalArgumentException("Unsupported configuration parameter value type "
          + value.getClass().getName());
    }

    return digest(digester, value);
  }

  @SuppressWarnings("unchecked")
  private static Serializable digest(@SuppressWarnings("rawtypes") Digester digester, Object value) {
    return digester.digest(value);
  }
}
