package io.takari.incremental;

import java.io.File;

/**
 * @see BuildContext#fileSetBuilder()
 * 
 * @noimplement clients are not expected to implement this interface.
 */
public interface FileSetBuilder {

  public FileSetBuilder withBasedir(File basedir);

  public FileSetBuilder withIncludes(Iterable<String> includes);

  public FileSetBuilder withExcludes(Iterable<String> includes);

  public FileSetBuilder addDefaultExcludes();

  // forDirectoriesOnly
  // forFilesOnly
  // caseInsensitive

  public FileSet build();

  public FileSet fromFiles(File... files);
}
