package io.takari.builder.internal.maven;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.SessionData;

import io.takari.builder.internal.digest.ClasspathDigester;

class MavenClasspathDigester extends ClasspathDigester {
  private static final String SESSION_DATA_KEY = MavenClasspathDigester.class.getName();

  MavenClasspathDigester(MavenSession session) {
    super(getCache(session));
  }

  @SuppressWarnings("unchecked")
  private static ConcurrentMap<String, byte[]> getCache(MavenSession session) {
    // this assumes that Aether repository session data does not change during reactor build
    SessionData sessionData = session.getRepositorySession().getData();
    if (sessionData.get(SESSION_DATA_KEY) == null) {
      sessionData.set(SESSION_DATA_KEY, null, new ConcurrentHashMap<String, byte[]>());
    }
    return (ConcurrentMap<String, byte[]>)sessionData.get(SESSION_DATA_KEY);
  }
}
