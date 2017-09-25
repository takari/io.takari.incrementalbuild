package io.takari.builder.internal.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

import io.takari.builder.internal.pathmatcher.JarEntries;

/**
 * A cache which keys on the path of a jar and returns the {@link JarEntries} from that jar
 * 
 * @author jaime.morales
 *
 */
public class JarEntriesCache {

  private static class Key {
    private final Path path;
    private final long length;
    private final long lastModified;

    public Key(Path path, long length, long lastModified) {
      this.path = path;
      this.length = length;
      this.lastModified = lastModified;
    }



    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + (int) (lastModified ^ (lastModified >>> 32));
      result = prime * result + (int) (length ^ (length >>> 32));
      result = prime * result + ((path == null) ? 0 : path.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      Key other = (Key) obj;
      if (lastModified != other.lastModified) return false;
      if (length != other.length) return false;
      if (path == null) {
        if (other.path != null) return false;
      } else if (!path.equals(other.path)) return false;
      return true;
    }
  }

  private final static JarEntriesCache INSTANCE = new JarEntriesCache();

  private JarEntriesCache() {}

  private final Map<Key, JarEntries> cache = new ConcurrentHashMap<>();

  public static JarEntriesCache get() {
    return INSTANCE;
  }

  public JarEntries get(Path path) {
    Key key = new Key(path, path.toFile().length(), path.toFile().lastModified());

    return cache.computeIfAbsent(key, (k) -> {
      try (JarFile jarFile = new JarFile(k.path.toFile())) {
        return new JarEntries(jarFile.entries());
      } catch (IOException e) {
        throw new UncheckedIOException("Unable to list Jar Entries", e);
      }
    });

  }
}
