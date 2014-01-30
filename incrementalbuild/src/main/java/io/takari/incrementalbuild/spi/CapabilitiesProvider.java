package io.takari.incrementalbuild.spi;

import java.util.Collection;

public interface CapabilitiesProvider {

  public Collection<String> getCapabilities(String namespace);

}
