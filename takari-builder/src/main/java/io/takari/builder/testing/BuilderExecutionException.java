/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.testing;

@SuppressWarnings("serial")
public class BuilderExecutionException extends Exception {

    public BuilderExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
