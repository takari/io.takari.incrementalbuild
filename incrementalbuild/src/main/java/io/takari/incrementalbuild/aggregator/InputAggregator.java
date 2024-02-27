/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.aggregator;

import io.takari.incrementalbuild.Output;
import java.io.File;
import java.io.IOException;

/**
 * Aggregation function. Only called when new output needs to be generated.
 */
public interface InputAggregator {

    /**
     * Creates aggregate output given the inputs.
     */
    public void aggregate(Output<File> output, Iterable<File> inputs) throws IOException;
}
