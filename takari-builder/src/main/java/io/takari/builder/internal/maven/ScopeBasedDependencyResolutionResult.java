/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.maven;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.project.DependencyResolutionResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;

class ScopeBasedDependencyResolutionResult implements DependencyResolutionResult {

    private DependencyNode root;

    private List<Dependency> dependencies = new ArrayList<>();

    private List<Dependency> resolvedDependencies = new ArrayList<>();

    private List<Dependency> unresolvedDependencies = new ArrayList<>();

    private List<Exception> collectionErrors = new ArrayList<>();

    private Map<Dependency, List<Exception>> resolutionErrors = new IdentityHashMap<>();

    @Override
    public DependencyNode getDependencyGraph() {
        return root;
    }

    public void setDependencyGraph(DependencyNode root) {
        this.root = root;
    }

    @Override
    public List<Dependency> getDependencies() {
        return dependencies;
    }

    @Override
    public List<Dependency> getResolvedDependencies() {
        return resolvedDependencies;
    }

    public void addResolvedDependency(Dependency dependency) {
        dependencies.add(dependency);
        resolvedDependencies.add(dependency);
    }

    @Override
    public List<Dependency> getUnresolvedDependencies() {
        return unresolvedDependencies;
    }

    @Override
    public List<Exception> getCollectionErrors() {
        return collectionErrors;
    }

    public void setCollectionErrors(List<Exception> exceptions) {
        if (exceptions != null) {
            this.collectionErrors = exceptions;
        } else {
            this.collectionErrors = new ArrayList<>();
        }
    }

    @Override
    public List<Exception> getResolutionErrors(Dependency dependency) {
        List<Exception> errors = resolutionErrors.get(dependency);
        return (errors != null) ? errors : Collections.<Exception>emptyList();
    }

    public void setResolutionErrors(Dependency dependency, List<Exception> errors) {
        dependencies.add(dependency);
        unresolvedDependencies.add(dependency);
        resolutionErrors.put(dependency, errors);
    }
}
