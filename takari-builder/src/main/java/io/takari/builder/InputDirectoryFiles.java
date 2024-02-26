/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Collection;

/**
 * Indicates that annotated parameter is a collection of input directory files that match
 * includes/excludes file name patters. Can be used with {@link Collection} or
 * {@link IDirectoryFiles} parameter types.
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface InputDirectoryFiles {

    /**
     * If {@code filesRequired=true}, the injected parameter value will be an existing directory that
     * has matching files.
     *
     * <p>
     * If {@code filesRequired=false}, the injected parameter value may be {@code null}, be a
     * directory that does not exist, existing directory with or without matching files.
     */
    boolean filesRequired() default false;

    String[] value() default {};

    String[] defaultValue() default {};

    /**
     * Ant-like resource name includes patterns. If specified, cannot be changed/overridden in pom.xml
     * {@code <configuration>} section.
     */
    String[] includes() default {};

    /**
     * Ant-like resource name default includes patterns.
     */
    String[] defaultIncludes() default {};

    String[] excludes() default {};

    String[] defaultExcludes() default {};
}
