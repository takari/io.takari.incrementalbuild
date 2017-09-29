package io.takari.builder.internal;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import io.takari.builder.internal.pathmatcher.PathMatcher;
import io.takari.builder.internal.pathmatcher.PathMatcher.Builder;
import io.takari.builder.internal.pathmatcher.PathNormalizer;
import io.takari.incrementalbuild.classpath.ClasspathEntriesSupplier;

@Named
@Singleton
public class ClasspathMatcher {

  private final List<ClasspathEntriesSupplier> suppliers;
  private PathMatcher matcher;

  @Inject
  public ClasspathMatcher(List<ClasspathEntriesSupplier> suppliers) {
    this.suppliers = suppliers;
  }

  public synchronized PathMatcher getMatcher() {
    if (matcher == null) {
      matcher = createMatcher();
    }
    return matcher;
  }

  private PathMatcher createMatcher() {
    Builder builder = PathMatcher.builder(PathNormalizer.createFSRoot());

    suppliers.stream().map(s -> s.entries()).flatMap(entries -> entries.stream())
        .forEach(entry -> builder.includePrefix(entry));

    return builder.build();
  }
}
