/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.maven;

import java.util.*;
import java.util.function.Function;
import org.apache.maven.project.MavenProject;

/**
 * Resolves MavenProject properties and a number of well-known "project.*" properties to their
 * string values. Unknown properties are resolved to {@code null} value.
 */
public class MavenProjectPropertyResolver implements Function<String, String> {

    private static final Map<String, Function<MavenProject, String>> EXPRESSIONS;

    static {
        Map<String, Function<MavenProject, String>> expressions = new HashMap<>();

        expressions.put("project.groupId", p -> p.getGroupId());
        expressions.put("project.artifactId", p -> p.getArtifactId());
        expressions.put("project.version", p -> p.getVersion());
        expressions.put("project.basedir", p -> p.getBasedir().getAbsolutePath());
        expressions.put("project.build.directory", p -> p.getBuild().getDirectory());
        expressions.put("project.build.outputDirectory", p -> p.getBuild().getOutputDirectory());
        expressions.put("project.build.testOutputDirectory", p -> p.getBuild().getTestOutputDirectory());

        EXPRESSIONS = Collections.unmodifiableMap(expressions);
    }

    private final MavenProject project;

    public MavenProjectPropertyResolver(MavenProject project) {
        this.project = project;
    }

    @Override
    public String apply(String name) {
        // check project properties first
        if (project.getProperties().containsKey(name)) {
            return project.getProperties().getProperty(name);
        }

        // supported project.* expressions
        Function<MavenProject, String> getter = EXPRESSIONS.get(name);
        if (getter != null) {
            return getter.apply(project);
        }

        return null;
    }
}
