/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.Output;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @noinstantiate clients are not expected to instantiate this class
 */
public class DefaultOutput extends DefaultResource<File> implements Output<File> {

    DefaultOutput(AbstractBuildContext context, DefaultBuildContextState state, File file) {
        super(context, state, file);
    }

    @Override
    public OutputStream newOutputStream() throws IOException {
        return context.newOutputStream(this);
    }

    // @Override
    // public <I> void associateInput(ResourceMetadata<I> input) {
    // context.associate((DefaultResourceMetadata<?>) input, this);
    // }

}
