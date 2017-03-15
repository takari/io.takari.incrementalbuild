package io.takari.builder.internal;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.DefaultPlexusContainer;

import io.takari.incrementalbuild.classpath.ClasspathEntriesSupplier;

@Named
@Singleton
public class MavenClasspathEntriesSupplier implements ClasspathEntriesSupplier {

  private final Collection<String> classpath = new LinkedHashSet<>();

  @Inject
  MavenClasspathEntriesSupplier(DefaultPlexusContainer plexus) {
    populateClasspath(plexus);
  }

  private void populateClasspath(DefaultPlexusContainer plexus) {
    // ALLOW maven core libraries, used to inject m2e workspace dependency resolver
    // this allows all class realms, which should only be maven core at this point
    plexus.getClassWorld().getRealms().stream() //
        .flatMap(r -> Arrays.asList(r.getURLs()).stream()) //
        .forEach(url -> {
          if ("file".equals(url.getProtocol())) {
            classpath.add(url.getPath());
          }
        });
  }

  @Override
  public Collection<String> entries() {
    return classpath;
  }

}
