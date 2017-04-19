package io.takari.incrementalbuild.classpath;

import java.util.Collection;

/**
 * 
 * Supplies a list of classpath entries to be allowed read-access from takari-builder framework
 *
 * @author jaime.morales
 */
public interface ClasspathEntriesSupplier {
  
  public Collection<String> entries();
  
}
