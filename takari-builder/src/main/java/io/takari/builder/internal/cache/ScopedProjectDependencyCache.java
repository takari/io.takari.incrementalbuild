/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.cache;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.ResolutionScope;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * This is a cache of scoped project dependency artifacts. Eliminates the need for multiple builders (or multiple
 * parameters within a builder) to resolve dependencies for the same scope.
 *
 * @author jaime.morales
 *
 */
@Named
@Singleton
public class ScopedProjectDependencyCache {

    public static class Key {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final ResolutionScope scope;
        private final boolean transitive;

        public Key(String groupId, String artifactId, String version, ResolutionScope scope, boolean transitive) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.scope = scope;
            this.transitive = transitive;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
            result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
            result = prime * result + ((scope == null) ? 0 : scope.hashCode());
            result = prime * result + (transitive ? 1231 : 1237);
            result = prime * result + ((version == null) ? 0 : version.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            Key other = (Key) obj;
            if (artifactId == null) {
                if (other.artifactId != null) return false;
            } else if (!artifactId.equals(other.artifactId)) return false;
            if (groupId == null) {
                if (other.groupId != null) return false;
            } else if (!groupId.equals(other.groupId)) return false;
            if (scope != other.scope) return false;
            if (transitive != other.transitive) return false;
            if (version == null) {
                if (other.version != null) return false;
            } else if (!version.equals(other.version)) return false;
            return true;
        }
    }

    private final Map<Key, Map<IArtifactMetadata, Path>> cache = new ConcurrentHashMap<>();

    public static Key key(
            String groupId, String artifactId, String version, ResolutionScope scope, boolean transitive) {
        return new Key(groupId, artifactId, version, scope, transitive);
    }

    /**
     * Returns a cached set of resolved artifacts for the given key
     *
     * @param key
     * @param supplier - will be called if the lookup returns no cached value
     * @return
     */
    public Map<IArtifactMetadata, Path> getDependencies(Key key, Supplier<Map<IArtifactMetadata, Path>> supplier) {
        Map<IArtifactMetadata, Path> dependencies = cache.get(key);

        if (dependencies == null) {
            dependencies = Collections.unmodifiableMap(new LinkedHashMap<>(supplier.get()));
            if (dependencies == null) {
                dependencies = Collections.emptyMap();
            }
            cache.put(key, dependencies);
        }

        return dependencies;
    }
}
