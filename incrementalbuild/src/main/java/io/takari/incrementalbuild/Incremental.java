/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional annotation that allows customization of how incremental build implementation handles
 * configuration parameters.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Incremental {

    public static enum Configuration {
        consider,

        /**
         * Indicates that annotated configuration parameter should be ignored by incremental build
         * implementation.
         */
        ignore
    }

    /**
     * Whether to {@link Configuration#consider} (the default) or {@link Configuration#ignore}
     * annotated configuration parameter.
     *
     * @return
     */
    Configuration configuration() default Configuration.consider;
}
