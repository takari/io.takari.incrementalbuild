package io.takari.incremental;

import java.io.File;

public interface FileSet {

  boolean contains(File resource);

}
