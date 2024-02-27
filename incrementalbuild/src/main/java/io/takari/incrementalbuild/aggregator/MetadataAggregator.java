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
import java.io.Serializable;
import java.util.Map;

public interface MetadataAggregator<T extends Serializable> {
    public Map<String, T> glean(File input) throws IOException;

    public void aggregate(Output<File> output, Map<String, T> metadata) throws IOException;
}
