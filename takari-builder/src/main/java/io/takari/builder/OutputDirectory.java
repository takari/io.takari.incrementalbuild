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
 * Indicates that annotated element is an output directory.
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface OutputDirectory {

    /**
     * @see Parameter#required()
     */
    boolean required() default true;

    /**
     * @see Parameter#value()
     */
    String[] value() default {};

    /**
     * @see Parameter#defaultValue()
     */
    String[] defaultValue() default {};

    // TODO decide if we want/need to provide includes/excludes patterns
}
