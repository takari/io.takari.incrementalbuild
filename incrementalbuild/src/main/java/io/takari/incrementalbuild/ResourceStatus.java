/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild;

public enum ResourceStatus {

    /**
     * Resource is new in this build, i.e. it was not present in the previous build.
     */
    NEW,

    /**
     * Resource changed since previous build.
     */
    MODIFIED,

    /**
     * Resource did not changed since previous build.
     */
    UNMODIFIED,

    /**
     * Resource was removed since previous build.
     */
    REMOVED;
}
