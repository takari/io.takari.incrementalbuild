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

/**
 * Indicates that annotated element is an input directory parameter.
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface InputDirectory {

    /**
     * If {@code filesRequired=true}, the injected parameter value will be an existing directory that
     * has matching files.
     *
     * <p>
     * If {@code filesRequired=false}, the injected parameter value may be {@code null}, be a
     * directory that does not exist, existing directory with or without matching files.
     */
    boolean filesRequired() default false;

    /**
     * Input directory location. If configured, the location cannot be changed/overridden in pom.xml
     * {@code <configuration>} section. Cannot be used together with {@link #defaultValue()}.
     *
     * @see Parameter#value()
     * @see #defaultValue()
     */
    String[] value() default {};

    /**
     * Default input directory location. Can be changed/overridden in pom.xml {@code <configuration>}
     * section. Cannot be used together with {@link #value()}.
     *
     * @see Parameter#defaultValue()
     * @see #value()
     */
    String[] defaultValue() default {};

    /**
     * Ant-like file includes patterns. Cannot be changed/overridden in pom.xml
     * {@code <configuration>} section.
     */
    String[] includes();

    /**
     * Ant-like file excludes patterns. No files are excluded by default. Cannot be changed/overridden
     * in pom.xml {@code <configuration>} section.
     */
    String[] excludes() default {};
}
