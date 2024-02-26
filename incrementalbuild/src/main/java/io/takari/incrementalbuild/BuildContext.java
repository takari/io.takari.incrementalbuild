/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Build context that supports 1..* input-output associations.
 */
public interface BuildContext {

    /**
     * Registers specified input {@code File} with this build context.
     *
     * @return {@link InputMetadata} representing the input file, never {@code null}.
     * @throws IllegalArgumentException if inputFile is not a file or cannot be read
     */
    public ResourceMetadata<File> registerInput(File inputFile);

    /**
     * Registers inputs identified by {@code basedir} and {@code includes}/{@code excludes} ant
     * patterns.
     * <p>
     * When a file is found under {@code basedir}, it will be registered if it does not match
     * {@code excludes} patterns and matches {@code includes} patterns. {@code null} or empty includes
     * parameter will match all files. {@code excludes} match takes precedence over {@code includes},
     * if a file matches one of excludes patterns it will not be registered regardless of includes
     * patterns match.
     * <p>
     * Implementation is not expected to handle changes {@code basedir}, {@code includes} or
     * {@code excludes} incrementally.
     *
     * @param basedir is the base directory to look for inputs, must not be {@code null}
     * @param includes patterns of the files to register, can be {@code null}
     * @param excludes patterns of the files to ignore, can be {@code null}
     * @see http://ant.apache.org/manual/dirtasks.html#patterns
     */
    public Iterable<? extends ResourceMetadata<File>> registerInputs(
            File basedir, Collection<String> includes, Collection<String> excludes) throws IOException;

    /**
     * Registers inputs identified by {@code basedir} and {@code includes}/{@code excludes} ant
     * patterns. Processes inputs that are new or modified since previous build.
     *
     * @returns processed inputs
     */
    public Iterable<? extends Resource<File>> registerAndProcessInputs(
            File basedir, Collection<String> includes, Collection<String> excludes) throws IOException;

    public void markSkipExecution();
}
