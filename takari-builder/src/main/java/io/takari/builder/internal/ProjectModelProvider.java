package io.takari.builder.internal;

import java.nio.file.Path;
import java.util.List;

public interface ProjectModelProvider {
  
  public Path getBasedir();
  
  public List<String> getCompileSourceRoots();
  
  public List<String> getTestCompileSourceRoots();

}
