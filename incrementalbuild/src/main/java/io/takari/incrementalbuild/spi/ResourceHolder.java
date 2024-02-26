/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.spi;

import io.takari.incrementalbuild.ResourceStatus;
import java.io.Serializable;

/**
 * Wraps input or output resource and logic and state necessary to determine if the resource has or
 * has not changed since the holder instance was created.
 *
 * <p>
 * Current implementation assumes R is some sort of serializable resource reference that can be used
 * to locate resource and determine resource up-to-date status against resource state captured in
 * resource holder instance. For example, to determine if an input has changed, implementation needs
 * to locate input's included inputs and check their status.
 *
 * @param <R> must provide correct implementation of {@code hashCode()} and {@code equals(Object)}
 */
public interface ResourceHolder<R extends Serializable> extends Serializable {

    /**
     * Returns resource handle, the resource does not have to exist.
     */
    public R getResource();

    public ResourceStatus getStatus();
}
