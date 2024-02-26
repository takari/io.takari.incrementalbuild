/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal;

import io.takari.builder.IArtifactMetadata;
import io.takari.builder.IDirectoryFiles;
import io.takari.builder.ResourceType;
import io.takari.builder.internal.Reflection.MultivalueFactory;
import io.takari.builder.internal.Reflection.ReflectionType;
import io.takari.builder.internal.digest.BytesHash;
import io.takari.builder.internal.digest.FileDigest;
import io.takari.builder.internal.digest.SHA1Digester;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Encapsulates builder effective configuration (aka, "expanded parameter values") and builder
 * instance factory.
 */
public class BuilderInputs {

    /**
     * Returns all input files used by the builder.
     */
    public Set<Path> getInputFiles() {
        return inputFiles;
    }

    /**
     * Returns all directories the builder can create output files in.
     */
    public Set<Path> getOutputDirectories() {
        return outputDirectories;
    }

    /**
     * Returns all specific declared files that builder can write.
     */
    public Set<Path> getOutputFiles() {
        return outputFiles;
    }

    public Set<ResourceRoot> getResourceRoots() {
        return resourceRoots;
    }

    public Set<CompileSourceRoot> getCompileSourceRoots() {
        return compileSourceRoots;
    }

    public boolean isNonDeterministic() {
        return isNonDeterministic;
    }

    /**
     * returns digest of this builder inputs configuration. the digest is Serializable and can be
     * persisted on filesystem between builder invocations. Persisted digest from the previous build
     * can be used to determine if builder inputs have changed or not.
     */
    public Digest getDigest() {
        Map<String, Value<?>> members = new LinkedHashMap<>();
        this.members.forEach((field, value) -> members.put(field.getName(), value));
        return digest(members);
    }

    public static Digest emptyDigest() {
        return new Digest();
    }

    /**
     * Creates and returns new fully configured builder instance.
     */
    public Object newBuilder() throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor();
        if (!constructor.isAccessible()) {
            constructor.setAccessible(true);
        }
        Object builder = constructor.newInstance();
        for (Map.Entry<Field, Value<?>> member : members.entrySet()) {
            Field field = member.getKey();
            if (!field.isAccessible()) {
                field.setAccessible(true);
            }
            field.set(builder, member.getValue().value());
        }
        return builder;
    }

    //
    // Everything below is the implementation
    //

    final Class<?> type;
    final Map<Field, Value<?>> members; // TODO change to Map<String, Value<?>>
    final Set<Path> inputFiles;
    final Set<Path> outputDirectories;
    final Set<Path> outputFiles;
    final Set<ResourceRoot> resourceRoots;
    final Set<CompileSourceRoot> compileSourceRoots;
    final boolean isNonDeterministic;

    // effective parameter configuration and factory to create injectable parameter values
    // allows analysis of effective configuration without loading builder classes
    public static interface Value<T> {
        public T value() throws ReflectiveOperationException;

        public default void accept(InputVisitor visitor) {}
        ;
    }

    static class StringValue implements Value<Object> {
        public final String configuration;
        public final Function<String, ?> converter;

        public StringValue(String configuration, Function<String, ?> converter) {
            this.configuration = configuration;
            this.converter = converter;
        }

        @Override
        public Object value() {
            return converter.apply(configuration);
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitString(this);
        }
    }

    abstract static class FileValue implements Value<Object> {
        final Class<?> parameterType;
        final Path configuration;

        protected FileValue(Class<?> parameterType, Path location) {
            this.parameterType = parameterType;
            this.configuration = location;
        }

        @Override
        public final Object value() {
            if (parameterType.isAssignableFrom(Path.class)) {
                return configuration;
            }
            return configuration.toFile();
        }
    }

    static class InputFileValue extends FileValue {
        public InputFileValue(Class<?> parameterType, Path location) {
            super(parameterType, location);
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitInputFile(this);
        }
    }

    static class OutputDirectoryValue extends FileValue {

        public OutputDirectoryValue(Class<?> parameterType, Path location) {
            super(parameterType, location);
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitOutputDirectory(this);
        }
    }

    static class OutputFileValue extends FileValue {

        public OutputFileValue(Class<?> parameterType, Path location) {
            super(parameterType, location);
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitOutputFile(this);
        }
    }

    static class GeneratedResourcesDirectoryValue extends FileValue {
        final ResourceType type;
        final List<String> includes;
        final List<String> excludes;

        public GeneratedResourcesDirectoryValue(
                Class<?> parameterType,
                Path location,
                ResourceType type,
                List<String> includes,
                List<String> excludes) {
            super(parameterType, location);
            this.type = type;
            this.includes = includes;
            this.excludes = excludes;
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitResourceRoot(this);
        }
    }

    static class GeneratedSourcesDirectoryValue extends FileValue {
        final ResourceType sourceType;

        public GeneratedSourcesDirectoryValue(Class<?> parameterType, Path location, ResourceType sourceType) {
            super(parameterType, location);
            this.sourceType = sourceType;
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitCompileSourceRoot(this);
        }
    }

    static class CompositeValue implements Value<Object> {
        final Class<?> type;
        final Map<Field, Value<?>> configuration;

        public CompositeValue(Class<?> type, Map<Field, Value<?>> configuration) {
            this.type = type;
            this.configuration = Collections.unmodifiableMap(configuration);
        }

        @Override
        public Object value() throws ReflectiveOperationException {
            Object value = type.newInstance();
            // boo, java8 streams don't work with checked exceptions used by reflection
            for (Map.Entry<Field, Value<?>> memberValue : configuration.entrySet()) {
                Field member = memberValue.getKey();
                if (!member.isAccessible()) {
                    member.setAccessible(true);
                }
                member.set(value, memberValue.getValue().value());
            }
            return value;
        }

        @Override
        public void accept(InputVisitor visitor) {
            configuration.values().forEach(v -> v.accept(visitor));
        }
    }

    @FunctionalInterface
    static interface InstanceFactory<T> {
        T newInstance() throws ReflectiveOperationException;
    }

    static class CollectionValue implements Value<Object> {
        final MultivalueFactory factory;
        final List<Value<?>> configuration;

        public CollectionValue(MultivalueFactory factory, List<Value<?>> elements) {
            this.factory = factory;
            this.configuration = Collections.unmodifiableList(elements);
        }

        @Override
        public Object value() throws ReflectiveOperationException {
            List<Object> elements = new ArrayList<>();
            for (Value<?> element : configuration) {
                elements.add(element.value());
            }
            return factory.newInstance(elements);
        }

        @Override
        public void accept(InputVisitor visitor) {
            configuration.forEach(v -> v.accept(visitor));
        }
    }

    static class MapValue implements Value<Map<String, ?>> {
        final InstanceFactory<Map<String, Object>> supplier;
        public final Map<String, String> configuration;
        public final Function<String, ?> converter;

        public MapValue(
                InstanceFactory<Map<String, Object>> supplier,
                Map<String, String> elements,
                Function<String, ?> converter) {
            this.supplier = supplier;
            this.configuration = Collections.unmodifiableMap(elements);
            this.converter = converter;
        }

        @Override
        public Map<String, Object> value() throws ReflectiveOperationException {
            Map<String, Object> map = supplier.newInstance();
            for (Map.Entry<String, String> element : configuration.entrySet()) {
                map.put(element.getKey(), converter.apply(element.getValue()));
            }
            return map;
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitMap(this);
        }
    }

    static class DependencyMapValue implements Value<Map<IArtifactMetadata, Object>> {
        final Class<?> type;
        final InstanceFactory<Map<IArtifactMetadata, Object>> supplier;
        public final Map<IArtifactMetadata, Path> elements;

        public DependencyMapValue(
                Class<?> type,
                InstanceFactory<Map<IArtifactMetadata, Object>> supplier,
                Map<IArtifactMetadata, Path> elements) {
            this.type = type;
            this.supplier = supplier;
            this.elements = Collections.unmodifiableMap(elements);
        }

        @Override
        public Map<IArtifactMetadata, Object> value() throws ReflectiveOperationException {
            Map<IArtifactMetadata, Object> map = supplier.newInstance();
            for (Map.Entry<IArtifactMetadata, Path> element : elements.entrySet()) {
                if (type.isAssignableFrom(File.class)) {
                    map.put(element.getKey(), element.getValue().toFile());
                } else if (type.isAssignableFrom(Path.class)) {
                    map.put(element.getKey(), element.getValue());
                }
            }
            return map;
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitDependencyMap(this);
        }
    }

    static class InputDirectoryValue implements Value<Object>, IDirectoryFiles {
        public final Class<?> type;

        public final Path location;
        public final Set<String> includes;
        public final Set<String> excludes;

        public Set<File> files;
        public final Set<Path> filePaths;
        public final Set<String> filenames;

        public InputDirectoryValue(
                Class<?> type,
                Path location,
                List<String> includes,
                List<String> excludes,
                Set<Path> files,
                Set<String> filenames) {
            this.type = type;
            this.location = location;
            this.includes = includes != null ? Collections.unmodifiableSet(new LinkedHashSet<>(includes)) : null;
            this.excludes = excludes != null ? Collections.unmodifiableSet(new LinkedHashSet<>(excludes)) : null;
            this.filePaths = Collections.unmodifiableSet(new LinkedHashSet<>(files));
            this.filenames = Collections.unmodifiableSet(new LinkedHashSet<>(filenames));
            this.files = null;
        }

        @Override
        public Object value() throws ReflectiveOperationException {
            if (type == File.class) {
                return location.toFile();
            } else if (type == Path.class) {
                return location;
            }
            return this;
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitInputDirectory(this);
        }

        @Override
        public File location() {
            return location.toFile();
        }

        @Override
        public Path locationPath() {
            return location;
        }

        @Override
        public Set<String> includes() {
            return includes;
        }

        @Override
        public Set<String> excludes() {
            return excludes;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<File> files() {
            if (files == null) {
                files = Collections.unmodifiableSet(filePaths.stream()
                        .map(f -> f.toFile())
                        .collect(Collectors.collectingAndThen(Collectors.toSet(), LinkedHashSet::new)));
            }
            return files;
        }

        @Override
        public Set<Path> filePaths() {
            return filePaths;
        }

        @Override
        public Set<String> filenames() {
            return filenames;
        }
    }

    static class InputFilesValue implements Value<Object> {
        final MultivalueFactory factory;
        final Set<Path> files;
        final ReflectionType type;

        public InputFilesValue(MultivalueFactory factory, Collection<Path> files, ReflectionType type) {
            this.factory = factory;
            this.files = new LinkedHashSet<>(files);
            this.type = type;
        }

        @Override
        public Object value() throws ReflectiveOperationException {
            // TODO support URL
            List<Object> members = this.files.stream().map(getMapper()).collect(Collectors.toList());
            return factory.newInstance(members);
        }

        private Function<Path, ?> getMapper() {
            Class<?> realType = type.isArray() ? type.adaptee().getComponentType() : type.adaptee();
            if (realType.isAssignableFrom(File.class)) {
                return Path::toFile;
            } else {
                return Function.identity();
            }
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitCollectionFileURL(this);
        }
    }

    static class ArtifactResourcesValue implements Value<Object> {
        /**
         * List of input files corresponding to these dependency resources. For exploded directory-based
         * artifacts, list of selected resources files. For jar-based artifacts, the artifact jar.
         */
        public final Set<Path> files;

        public final IArtifactMetadata artifact;
        public final Set<URL> urls;

        public ArtifactResourcesValue(Set<Path> files, IArtifactMetadata artifact, Collection<URL> urls) {
            this.files = Collections.unmodifiableSet(files);
            this.artifact = artifact;
            TreeSet<URL> sorted = new TreeSet<>((a, b) -> a.getPath().compareTo(b.getPath()));
            sorted.addAll(urls);
            this.urls = Collections.unmodifiableSet(sorted);
        }

        @Override
        public Object value() throws ReflectiveOperationException {
            return new ArtifactResourcesImpl(artifact, urls);
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitArtifactResources(this);
        }
    }

    /**
     * Artificial Value that aggregates resources from {@code List<ArtifactResourcesValue>} to
     * {@code Collection<URL>}.
     */
    static class ListArtifactResourcesValue implements Value<Object> {
        final MultivalueFactory factory;
        final List<ArtifactResourcesValue> members;

        public ListArtifactResourcesValue(MultivalueFactory factory, List<ArtifactResourcesValue> members) {
            this.factory = factory;
            this.members = Collections.unmodifiableList(members);
        }

        @Override
        public Object value() throws ReflectiveOperationException {
            // keep artifact order, it is significant when different dependencies have the same resource
            // assumes artifact urls are sorted by their paths already
            LinkedHashSet<URL> urls = new LinkedHashSet<>();
            for (ArtifactResourcesValue member : members) {
                urls.addAll(member.urls);
            }
            return factory.newInstance(urls);
        }

        @Override
        public void accept(InputVisitor visitor) {
            for (ArtifactResourcesValue member : members) {
                member.accept(visitor);
            }
        }
    }

    static class DependencyValue implements Value<Object> {
        final Class<?> type;
        final IArtifactMetadata artifact;
        final Path location;

        protected DependencyValue(Class<?> type, IArtifactMetadata artifact, Path location) {
            this.type = type;
            this.artifact = artifact;
            this.location = location;
        }

        @Override
        public final Object value() {
            if (File.class.isAssignableFrom(type)) {
                return location.toFile();
            } else if (Path.class.isAssignableFrom(type)) {
                return location;
            } else if (IArtifactMetadata.class.isAssignableFrom(type)) {
                return artifact;
            }
            throw new IllegalStateException();
        }

        @Override
        public void accept(InputVisitor visitor) {
            visitor.visitDependency(this);
        }

        public boolean isFileType() {
            return File.class.isAssignableFrom(type) || Path.class.isAssignableFrom(type);
        }
    }

    static interface InputVisitor {
        public void visitInputFile(InputFileValue inputFile);

        public void visitInputDirectory(InputDirectoryValue value);

        public void visitOutputDirectory(OutputDirectoryValue value);

        public void visitOutputFile(OutputFileValue value);

        public void visitResourceRoot(GeneratedResourcesDirectoryValue value);

        public void visitCompileSourceRoot(GeneratedSourcesDirectoryValue value);

        public void visitString(StringValue value);

        public void visitMap(MapValue value);

        public void visitCollectionFileURL(InputFilesValue value);

        public void visitArtifactResources(ArtifactResourcesValue value);

        public void visitDependency(DependencyValue value);

        public void visitDependencyMap(DependencyMapValue value);
    }

    @SuppressWarnings("serial")
    static class Digest implements Serializable {
        // no serialVersionUID, want deserialization to fail if state format changes

        private final Map<String, BytesHash> inputs;
        private final Map<File, FileDigest> files;

        private Digest(Map<String, BytesHash> inputs, Map<File, FileDigest> files) {
            if (inputs == null || files == null) {
                throw new IllegalArgumentException();
            }

            this.inputs = inputs;
            this.files = files;
        }

        private Digest() {
            this.inputs = null;
            this.files = null;
        }

        public Set<Path> files() {
            return this.files == null
                    ? Collections.emptySet()
                    : this.files.keySet().stream()
                            .map(f -> f.toPath())
                            .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public boolean equals(Object obj) {
            if (inputs == null || files == null) {
                return false; // empty digest isn't equal to anything
            }

            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Digest)) {
                return false;
            }
            Digest other = (Digest) obj;
            return inputs.equals(other.inputs) //
                    && files.equals(other.files);
        }
    }

    static Digest digest(Value<?> input) {
        return digest(Collections.singletonMap("parameter", input));
    }

    private static Digest digest(Map<String, Value<?>> members) {
        TreeMap<String, BytesHash> memberDigests = new TreeMap<>();
        TreeMap<File, FileDigest> fileDigests = new TreeMap<>();

        for (Map.Entry<String, Value<?>> member : members.entrySet()) {
            MessageDigest digester = SHA1Digester.newInstance();

            member.getValue().accept(new InputVisitor() {

                private void digestInput(Path value) {
                    if (Files.isRegularFile(value)) {
                        fileDigests.put(value.toFile(), FileDigest.digest(value));
                    } else if (Files.isDirectory(value)) {
                        try {
                            Files.walk(value) //
                                    .filter(p -> Files.isRegularFile(p)) //
                                    .forEach(f -> fileDigests.put(f.toFile(), FileDigest.digest(f)));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    } else {
                        // does not exist
                        digest(value.toString());
                    }
                }

                private void digestInput(URL url) {
                    digest(url.getPath());
                    try (InputStream is = url.openStream()) {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = is.read(buf)) >= 0) {
                            digester.update(buf, 0, len);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                private void digest(Path value) {
                    digester.update(value.toString().getBytes(UTF8));
                }

                private void digest(String value) {
                    if (value != null) {
                        digester.update(value.getBytes(UTF8));
                    }
                }

                private void digest(IArtifactMetadata value) {
                    digest(value.getGroupId());
                    digest(value.getArtifactId());
                    digest(value.getVersion());
                    digest(value.getType());
                    digest(value.getClassifier());
                }

                private void digest(byte value) {
                    digester.update(value);
                }

                @Override
                public void visitOutputDirectory(OutputDirectoryValue outputDirectory) {
                    digest(outputDirectory.configuration);
                }

                @Override
                public void visitOutputFile(OutputFileValue outputFile) {
                    digest(outputFile.configuration);
                }

                @Override
                public void visitInputFile(InputFileValue value) {
                    digestInput(value.configuration);
                }

                @Override
                public void visitInputDirectory(InputDirectoryValue value) {
                    digest(value.location.toString());
                    if (value.includes != null) {
                        value.includes.forEach(include -> digest(include));
                    }
                    if (value.excludes != null) {
                        value.excludes.forEach(exclude -> digest(exclude));
                    }
                    value.filePaths.forEach(file -> digestInput(file));
                }

                @Override
                public void visitResourceRoot(GeneratedResourcesDirectoryValue value) {
                    digest(value.configuration);
                    if (value.includes != null) {
                        value.includes.forEach(include -> digest(include));
                    }
                    if (value.excludes != null) {
                        value.excludes.forEach(exclude -> digest(exclude));
                    }
                    digest((byte) value.type.ordinal());
                }

                @Override
                public void visitCompileSourceRoot(GeneratedSourcesDirectoryValue value) {
                    digest(value.configuration);
                    digest((byte) value.sourceType.ordinal());
                }

                @Override
                public void visitString(StringValue simple) {
                    digest(simple.configuration);
                }

                @Override
                public void visitMap(MapValue value) {
                    Map<String, ?> map = value.configuration;
                    if (map != null) {
                        map.entrySet().forEach(entry -> digest(entry.toString()));
                    }
                }

                @Override
                public void visitDependencyMap(DependencyMapValue value) {
                    Map<IArtifactMetadata, Path> map = value.elements;
                    if (map != null) {
                        map.entrySet().forEach(entry -> {
                            digest(entry.getKey());
                            digestInput(entry.getValue());
                        });
                    }
                }

                @Override
                public void visitCollectionFileURL(InputFilesValue value) {
                    value.files.forEach(file -> digestInput(file));
                }
                ;

                @Override
                public void visitArtifactResources(ArtifactResourcesValue value) {
                    digest(value.artifact);
                    value.urls.forEach(url -> digestInput(url));
                }

                @Override
                public void visitDependency(DependencyValue value) {
                    digest(value.artifact);
                    if (value.isFileType()) {
                        digestInput(value.location);
                    }
                }
            });
            memberDigests.put(member.getKey(), new BytesHash(digester.digest()));
        }

        return new Digest(memberDigests, fileDigests);
    }

    BuilderInputs(Class<?> type, Map<Field, Value<?>> values, boolean isNonDeterministic) {
        this.type = type;
        this.members = Collections.unmodifiableMap(values);
        this.isNonDeterministic = isNonDeterministic;

        Set<Path> inputFiles = new LinkedHashSet<>();
        Set<Path> outputDirectories = new LinkedHashSet<>();
        Set<Path> outputFiles = new LinkedHashSet<>();
        Set<ResourceRoot> resources = new LinkedHashSet<>();
        Set<CompileSourceRoot> compileSourceRoots = new LinkedHashSet<>();

        InputVisitor visitor = new InputVisitor() {

            @Override
            public void visitInputFile(InputFileValue value) {
                inputFiles.add(value.configuration);
            }

            @Override
            public void visitInputDirectory(InputDirectoryValue value) {
                inputFiles.addAll(value.filePaths);
            }

            @Override
            public void visitOutputDirectory(OutputDirectoryValue value) {
                outputDirectories.add(value.configuration);
            }

            @Override
            public void visitOutputFile(OutputFileValue value) {
                outputFiles.add(value.configuration);
            }

            @Override
            public void visitResourceRoot(GeneratedResourcesDirectoryValue value) {
                outputDirectories.add(value.configuration);
                resources.add(new ResourceRoot(
                        value.configuration.toAbsolutePath().toString(), value.type, value.includes, value.excludes));
            }

            @Override
            public void visitCompileSourceRoot(GeneratedSourcesDirectoryValue value) {
                outputDirectories.add(value.configuration);
                compileSourceRoots.add(new CompileSourceRoot(
                        value.configuration.toAbsolutePath().toString(), value.sourceType));
            }

            @Override
            public void visitString(StringValue value) {}

            @Override
            public void visitMap(MapValue value) {}

            @Override
            public void visitCollectionFileURL(InputFilesValue value) {
                value.files.forEach(file -> inputFiles.add(file));
            }

            @Override
            public void visitArtifactResources(ArtifactResourcesValue value) {
                inputFiles.addAll(value.files);
            }

            @Override
            public void visitDependency(DependencyValue value) {
                if (value.isFileType()) {
                    addDependencyInput(inputFiles, value.location);
                }
            }

            @Override
            public void visitDependencyMap(DependencyMapValue value) {
                Map<IArtifactMetadata, Path> map = value.elements;
                if (map != null) {
                    map.entrySet().forEach(entry -> {
                        addDependencyInput(inputFiles, entry.getValue());
                    });
                }
            }

            private void addDependencyInput(Set<Path> inputFiles, Path path) {
                if (Files.isRegularFile(path)) {
                    inputFiles.add(path);
                } else if (Files.isDirectory(path)) {
                    addFilesFromDirectory(inputFiles, path);
                }
            }

            private void addFilesFromDirectory(Set<Path> inputFiles, Path path) {
                try {
                    Files.walk(path).filter(Files::isRegularFile).forEach(p -> inputFiles.add(p));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };

        values.values().forEach(member -> member.accept(visitor));

        this.inputFiles = Collections.unmodifiableSet(inputFiles);
        this.outputDirectories = Collections.unmodifiableSet(outputDirectories);
        this.outputFiles = Collections.unmodifiableSet(outputFiles);
        this.resourceRoots = Collections.unmodifiableSet(resources);
        this.compileSourceRoots = Collections.unmodifiableSet(compileSourceRoots);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
}
