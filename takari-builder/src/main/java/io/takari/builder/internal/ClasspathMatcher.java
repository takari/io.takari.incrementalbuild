/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal;

import io.takari.builder.internal.pathmatcher.PathMatcher;
import io.takari.builder.internal.pathmatcher.PathMatcher.Builder;
import io.takari.builder.internal.pathmatcher.PathNormalizer;
import io.takari.incrementalbuild.classpath.ClasspathEntriesSupplier;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class ClasspathMatcher {

    private final List<ClasspathEntriesSupplier> suppliers;
    private PathMatcher matcher;

    @Inject
    public ClasspathMatcher(List<ClasspathEntriesSupplier> suppliers) {
        this.suppliers = suppliers;
    }

    public synchronized PathMatcher getMatcher() {
        if (matcher == null) {
            matcher = createMatcher();
        }
        return matcher;
    }

    private PathMatcher createMatcher() {
        Builder builder = PathMatcher.builder(PathNormalizer.createNormalizer());

        suppliers.stream()
                .map(s -> s.entries())
                .flatMap(entries -> entries.stream())
                .forEach(entry -> builder.includePrefix(entry));

        return builder.build();
    }
}
