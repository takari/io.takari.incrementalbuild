package io.takari.incrementalbuild.util;

import io.takari.incrementalbuild.BuildContext;

import java.io.File;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Convenience class that adapts {@link DirectoryScanner} to {@link Iterable} and can be passed to
 * {@link BuildContext#registerInputs(Iterable)} without intermediate collection of included
 * {@link File}s.
 */
public class DirectoryScannerAdapter implements Iterable<File> {

  private final DirectoryScanner scanner;

  private static class DirectoryScanIterator implements Iterator<File> {

    private final File basedir;
    private final String[] paths;
    private int idx;

    public DirectoryScanIterator(File basedir, String[] paths) {
      this.basedir = basedir;
      this.paths = paths;
    }

    @Override
    public boolean hasNext() {
      return paths != null && idx < paths.length;
    }

    @Override
    public File next() {
      if (hasNext()) {
        return new File(basedir, paths[idx++]);
      }
      throw new NoSuchElementException();
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

  }

  public DirectoryScannerAdapter(DirectoryScanner scanner) {
    this.scanner = scanner;
    scanner.scan();
  }

  @Override
  public Iterator<File> iterator() {
    return new DirectoryScanIterator(scanner.getBasedir(), scanner.getIncludedFiles());
  }
}
