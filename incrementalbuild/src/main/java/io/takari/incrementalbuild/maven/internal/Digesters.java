package io.takari.incrementalbuild.maven.internal;

import io.takari.incrementalbuild.configuration.Configuration;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;

class Digesters {

  // dies with class not found error if UTF-8 charset is not present
  static final Charset UTF_8 = Charset.forName("UTF-8");


  public static class UnsupportedParameterTypeException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    final Class<?> type;

    public UnsupportedParameterTypeException(Class<?> type) {
      this.type = type;
    }
  }

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

  private static final Digester<MavenProject> DIGESTER_MAVENPROJECT = new Digester<MavenProject>() {
    @Override
    public Serializable digest(MavenProject value) {
      final MessageDigest digester = SHA1Digester.newInstance();

      // effective pom.xml defines project configuration, rebuild whenever project configuration
      // changes we can't be more specific here because mojo can access entire project model, not
      // just its own configuration
      try {
        new MavenXpp3Writer().write(new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            digester.update((byte) b);
          }
        }, value.getModel());
      } catch (IOException e) {
        // can't happen
      }

      return new BytesHash(digester.digest());
    }
  };

  private static final Digester<MavenSession> DIGESTER_MAVENSESSION = new Digester<MavenSession>() {
    @Override
    @SuppressWarnings("deprecation")
    public Serializable digest(MavenSession session) {
      // execution properties define build parameters passed in from command line and jvm used
      SortedMap<String, String> executionProperties = new TreeMap<String, String>();

      for (Map.Entry<Object, Object> property : session.getExecutionProperties().entrySet()) {
        // TODO unit test non-string keys do not cause problems at runtime
        // TODO test if non-string values can or cannot be used
        Object key = property.getKey();
        Object value = property.getValue();
        if (key instanceof String && value instanceof String) {
          executionProperties.put(key.toString(), value.toString());
        }
      }

      // m2e workspace launch
      executionProperties.remove("classworlds.conf");

      Iterator<Map.Entry<String, String>> iter = executionProperties.entrySet().iterator();
      while (iter.hasNext()) {
        Map.Entry<String, String> property = iter.next();

        // Environment has PID of java process (env.JAVA_MAIN_CLASS_<PID>), SSH_AGENT_PID, unique
        // TMPDIR (on OSX)
        // and other volatile variables.
        if (property.getKey().startsWith("env.")) {
          iter.remove();
        }
      }

      MessageDigest digester = SHA1Digester.newInstance();

      for (Map.Entry<String, String> property : executionProperties.entrySet()) {
        digester.update(property.getKey().getBytes(UTF_8));
        digester.update(property.getValue().getBytes(UTF_8));
      }

      return new BytesHash(digester.digest());
    }
  };

  private static final Digester<Collection<?>> DIGESTER_COLLECTION = new Digester<Collection<?>>() {
    @Override
    public Serializable digest(Collection<?> collection) {
      // TODO consider collapsing to single SHA1 hash
      ArrayList<Serializable> digest = new ArrayList<Serializable>();
      for (Object element : collection) {
        Serializable elementDigest = Digesters.digest(element);
        if (elementDigest != null) {
          digest.add(elementDigest);
        }
      }
      return digest;
    }
  };

  private static Digester<Artifact> DIGESTER_ARTIFACT = new Digester<Artifact>() {
    @Override
    public Serializable digest(Artifact value) {
      return value.getFile();
    }
  };

  private static Digester<ArtifactRepository> DIGESTER_ARTIFACTREPOSITORY =
      new Digester<ArtifactRepository>() {
        @Override
        public Serializable digest(ArtifactRepository value) {
          return value.getUrl();
        }
      };

  static {
    Map<Class<?>, Digester<?>> digesters = new LinkedHashMap<Class<?>, Digester<?>>();
    // common Maven objects
    digesters.put(ArtifactRepository.class, DIGESTER_ARTIFACTREPOSITORY);
    digesters.put(Artifact.class, DIGESTER_ARTIFACT);
    digesters.put(MavenProject.class, DIGESTER_MAVENPROJECT);
    digesters.put(MavenSession.class, DIGESTER_MAVENSESSION);
    //
    digesters.put(Collection.class, DIGESTER_COLLECTION);
    //
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

    return digest(value);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Serializable digest(Object value) {
    return ((Digester) getDigester(value)).digest(value);
  }

  private static Digester<?> getDigester(Object value) {
    Digester<?> digester = null;
    for (Map.Entry<Class<?>, Digester<?>> entry : DIGESTERS.entrySet()) {
      if (entry.getKey().isInstance(value)) {
        digester = entry.getValue();
        break;
      }
    }
    if (digester == null) {
      throw new UnsupportedParameterTypeException(value.getClass());
    }
    return digester;
  }
}
