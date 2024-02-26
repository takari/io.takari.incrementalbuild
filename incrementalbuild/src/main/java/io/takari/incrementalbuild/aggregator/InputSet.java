/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.aggregator;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;

/**
 * Represents input set being aggregated.
 */
public interface InputSet {
    public File addInput(File inputFile) throws IOException;

    public Iterable<File> addInputs(File basedir, Collection<String> includes, Collection<String> excludes)
            throws IOException;

    public boolean aggregateIfNecessary(File outputFile, InputAggregator aggregator) throws IOException;

    public <T extends Serializable> boolean aggregateIfNecessary(File outputFile, MetadataAggregator<T> aggregator)
            throws IOException;
}
