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
import java.util.Set;

/**
 * Helper interface to encapsulate inputs selected from a particular directory.
 */
public interface IDirectoryFiles {

    File location();

    Path locationPath();

    Set<String> includes();

    Set<String> excludes();

    Set<File> files();

    Set<Path> filePaths();

    /**
     * Relative file names. Forward slash (i.e. '/') is used as path separator on all platforms.
     */
    Set<String> filenames();
}
