/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild;

import java.io.File;

/**
 * Build context that tracks inputs and outputs but not associations among them.
 * <p>
 * If there are new, changed or removed inputs, all outputs must be recreated. Outputs that are not
 * recreated are considered obsolete and will be deleted at the end of the build.
 * <p>
 */
public interface BasicBuildContext {
    public ResourceMetadata<File> registerInput(File inputFile);

    public boolean isProcessingRequired();

    public Output<File> processOutput(File outputFile);
}
