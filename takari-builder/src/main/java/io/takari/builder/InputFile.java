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
 * Identifies single input file parameter.
 */
@Target({FIELD})
@Retention(RUNTIME)
public @interface InputFile {

    /**
     * If {@code fileRequired=true}, the injected parameter value will be an existing regular file.
     *
     * <p>
     * If {@code fileRequired=false}, the injected parameter value can be am existing regular file, a
     * file that does not exist or {@code null}, if no parameter configuration was provided.
     *
     * @see Parameter#required()
     */
    boolean fileRequired() default false;

    /**
     * @see Parameter#value()
     */
    String[] value() default {};

    /**
     * @see Parameter#defaultValue()
     */
    String[] defaultValue() default {};
}
