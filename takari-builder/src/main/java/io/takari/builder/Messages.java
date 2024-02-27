/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder;

import java.io.File;
import java.nio.file.Path;

// TODO Messager is probably a better name
public interface Messages {
    void info(File resource, int line, int column, String message, Throwable cause);

    void warn(File resource, int line, int column, String message, Throwable cause);

    void error(File resource, int line, int column, String message, Throwable cause);

    void info(Path resource, int line, int column, String message, Throwable cause);

    void warn(Path resource, int line, int column, String message, Throwable cause);

    void error(Path resource, int line, int column, String message, Throwable cause);
}
