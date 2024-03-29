/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.aggregator.internal;

import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.aggregator.InputAggregator;
import io.takari.incrementalbuild.aggregator.InputSet;
import io.takari.incrementalbuild.aggregator.MetadataAggregator;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultInputSet implements InputSet {

    private final DefaultAggregatorBuildContext context;

    private final Set<File> inputs = new LinkedHashSet<>();

    DefaultInputSet(DefaultAggregatorBuildContext context) {
        this.context = context;
    }

    @Override
    public File addInput(File inputFile) throws IOException {
        inputFile = context.registerInput(inputFile).getResource();
        inputs.add(inputFile);
        return inputFile;
    }

    @Override
    public Iterable<File> addInputs(File basedir, Collection<String> includes, Collection<String> excludes)
            throws IOException {
        Set<File> inputs = new LinkedHashSet<>();
        for (ResourceMetadata<File> inputMetadata : context.registerInputs(basedir, includes, excludes)) {
            this.inputs.add(inputMetadata.getResource());
            inputs.add(inputMetadata.getResource());
        }
        return inputs;
    }

    @Override
    public boolean aggregateIfNecessary(File outputFile, InputAggregator aggregator) throws IOException {
        return context.aggregateIfNecessary(inputs, outputFile, aggregator);
    }

    @Override
    public <T extends Serializable> boolean aggregateIfNecessary(File outputFile, MetadataAggregator<T> aggregator)
            throws IOException {
        return context.aggregateIfNecessary(inputs, outputFile, aggregator);
    }
}
