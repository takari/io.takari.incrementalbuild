/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.enforcer.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnforcerConfig {

    public static final String ALL_BUILDERS = "*";

    protected static final Logger log = LoggerFactory.getLogger(EnforcerConfig.class);

    public static EnforcerConfig fromFile(Path config) {
        Builder builder = new Builder();

        if (config == null) {
            return builder.build();
        }

        boolean enforce = false;

        try (BufferedReader r = new BufferedReader(new InputStreamReader(Files.newInputStream(config)))) {
            int lineno = 0;
            String str;
            while ((str = r.readLine()) != null) {
                str = str.trim();

                if (lineno == 0 && "# enforce".equals(str)) {
                    enforce = true;

                    continue;
                }

                if (str.startsWith("#") || str.isEmpty()) {
                    continue;
                }

                List<String> parts = Arrays.asList(str.split("\\s+")).stream()
                        .map(s -> s.trim())
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

                if (parts.size() != 3
                        && (parts.size() != 2 && (Type.EXCLUDE.is(parts.get(1)) || Type.NETWORK.is(parts.get(1))))) {
                    log.warn("Ignored illegal configuration line #{} : wrong number of parts", lineno);
                    continue;
                }

                String identifier = parts.get(0);
                String action = parts.get(1);
                String path = parts.size() == 2 ? null : parts.get(2);
                Type type = Type.get(action);

                if (type == null) {
                    log.warn("Ignored illegal configuration line #{} : wrong action {}", lineno, action);
                    continue;
                }

                // if (!typeSupported(type)) {
                // log.warn("Ignored illegal configuration line #{} : wrong action {}", lineno, action);
                // continue;
                // }

                switch (type) {
                    case READ:
                        builder.withReadException(identifier, path);
                        break;
                    case READ_AND_TRACK:
                        builder.withReadAndTrackException(identifier, path);
                        break;
                    case WRITE:
                        builder.withWriteException(identifier, path);
                        break;
                    case EXECUTE:
                        builder.withExecException(identifier, path);
                        break;
                    case EXCLUDE:
                        builder.withExclusion(identifier);
                        break;
                    case NETWORK:
                        builder.withNetworkException(identifier);
                        break;
                }
                lineno++;
            }
        } catch (NoSuchFileException e) {
            // file not found. ok
        } catch (IOException e) {
            log.error("Could not read enforcer config file {}", config, e);
        }
        builder.enforce(enforce);

        return builder.build();
    }

    protected static enum Type {
        EXCLUDE("P"), // I would prefer X, but this represents project exclusions in M/E whitelist and
        // I'd rather not change
        NETWORK("N"),
        EXECUTE("E"),
        READ("R"),
        READ_AND_TRACK("RT"),
        WRITE("W");

        private final String key;

        private Type(String key) {
            this.key = key;
        }

        private boolean is(String key) {
            return this.key.equals(key);
        }

        private static Type get(String key) {
            for (Type t : values()) {
                if (t.key.equals(key)) {
                    return t;
                }
            }
            return null;
        }
    }

    public static class Builder {

        final Map<String, Collection<String>> readExceptions = new LinkedHashMap<>();
        final Map<String, Collection<String>> readAndTrackExceptions = new LinkedHashMap<>();
        final Map<String, Collection<String>> writeExceptions = new LinkedHashMap<>();
        final Map<String, Collection<String>> execExceptions = new LinkedHashMap<>();
        final List<String> exclusions = new ArrayList<>();
        final List<String> networkExceptions = new ArrayList<>();

        protected boolean enforce;

        private Builder() {}

        public Builder enforce(boolean enforce) {
            this.enforce = enforce;
            return this;
        }

        public Builder withReadException(String identifier, String path) {
            this.putToMultimap(readExceptions, identifier, path);

            return this;
        }

        public Builder withReadAndTrackException(String identifier, String path) {
            this.putToMultimap(readAndTrackExceptions, identifier, path);

            return this;
        }

        public Builder withWriteException(String identifier, String path) {
            this.putToMultimap(writeExceptions, identifier, path);

            return this;
        }

        public Builder withExecException(String identifier, String command) {
            this.putToMultimap(execExceptions, identifier, command);

            return this;
        }

        public Builder withExclusion(String exclusion) {
            this.exclusions.add(exclusion);

            return this;
        }

        public Builder withNetworkException(String networkException) {
            this.networkExceptions.add(networkException);

            return this;
        }

        public EnforcerConfig build() {
            return new EnforcerConfig(
                    enforce,
                    readExceptions,
                    readAndTrackExceptions,
                    writeExceptions,
                    execExceptions,
                    exclusions,
                    networkExceptions);
        }

        //
        //

        private void putToMultimap(Map<String, Collection<String>> multimap, String key, String value) {
            Collection<String> c = multimap.get(key);
            if (c == null) {
                c = new LinkedHashSet<>();
                multimap.put(key, c);
            }
            c.add(value);
        }
    }

    protected final Map<String, Collection<String>> readExceptions;
    protected final Map<String, Collection<String>> readAndTrackExceptions;
    protected final Map<String, Collection<String>> writeExceptions;
    protected final Map<String, Collection<String>> execExceptions;
    protected final List<String> exclusions;
    protected final List<String> networkExceptions;

    protected final boolean enforce;

    private EnforcerConfig(
            boolean enforce,
            Map<String, Collection<String>> readExceptions,
            Map<String, Collection<String>> readAndTrackExceptions,
            Map<String, Collection<String>> writeExceptions,
            Map<String, Collection<String>> execExceptions,
            List<String> exclusions,
            List<String> networkExceptions) {
        this.enforce = enforce;
        this.readExceptions = Collections.unmodifiableMap(readExceptions);
        this.readAndTrackExceptions = Collections.unmodifiableMap(readAndTrackExceptions);
        this.writeExceptions = Collections.unmodifiableMap(writeExceptions);
        this.execExceptions = Collections.unmodifiableMap(execExceptions);
        this.exclusions = Collections.unmodifiableList(exclusions);
        this.networkExceptions = Collections.unmodifiableList(networkExceptions);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EnforcerConfig empty() {
        return new Builder().enforce(true).build();
    }

    public Collection<String> getReadExceptions(String identifier) {
        return getExceptions(identifier, readExceptions);
    }

    public Collection<String> getReadAndTrackExceptions(String identifier) {
        return getExceptions(identifier, readAndTrackExceptions);
    }

    public Collection<String> getWriteExceptions(String identifier) {
        return getExceptions(identifier, writeExceptions);
    }

    public Collection<String> getExecExceptions(String identifier) {
        return getExceptions(identifier, execExceptions);
    }

    private static Collection<String> getExceptions(String identifier, Map<String, Collection<String>> exceptionMap) {
        Collection<String> exceptions = ALL_BUILDERS.equals(identifier)
                ? exceptionMap.get(ALL_BUILDERS)
                : concat(exceptionMap.get(ALL_BUILDERS), exceptionMap.get(identifier));

        return exceptions == null ? Collections.emptyList() : Collections.unmodifiableCollection(exceptions);
    }

    public boolean enforce() {
        return enforce;
    }

    public boolean exclude(String identifier) {
        return exclusions.contains(identifier);
    }

    public boolean allowNetworkAccess(String identifier) {
        return networkExceptions.contains(identifier);
    }

    private static <T> Collection<T> concat(Collection<? extends T> a, Collection<? extends T> b) {
        return Stream.concat(a != null ? a.stream() : Stream.empty(), b != null ? b.stream() : Stream.empty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean hasEntriesFor(String identifier) {
        if (Stream.of(readExceptions, readAndTrackExceptions, writeExceptions, execExceptions)
                .anyMatch(m -> m.containsKey(identifier))) {
            return true;
        }
        if (Stream.of(exclusions, networkExceptions).anyMatch(l -> l.contains(identifier))) {
            return true;
        }
        return false;
    }

    public boolean hasWildcardEntries() {
        if (Stream.of(readExceptions, readAndTrackExceptions, writeExceptions, execExceptions)
                .anyMatch(m -> m.containsKey(ALL_BUILDERS))) {
            return true;
        }
        return false;
    }
}
