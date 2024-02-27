/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal;

import java.io.IOException;

/**
 * Wraps {@link IOException} raises while writing builder execution undo log file.
 *
 * Results in immediate ungraceful build execution termination.
 */
@SuppressWarnings("serial")
class IncrementalBuildException extends RuntimeException {
    public IncrementalBuildException(IOException cause) {
        super(cause);
    }
}
