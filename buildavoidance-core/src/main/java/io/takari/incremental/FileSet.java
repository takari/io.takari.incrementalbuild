package io.takari.incremental;

import java.io.File;

/**
 * @see BuildContext#fileSetBuilder()
 * @see FileSetBuilder
 * 
 * @noimplement clients are not expected to implement this interface.
 */
public interface FileSet extends Iterable<File> {

  boolean contains(File resource);

}
