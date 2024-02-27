/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.model;

import java.util.List;

public interface TypeAdapter {

    String simpleName();

    // fully qualifier type name
    String qualifiedName();

    boolean isPrimitive();

    boolean isIterable();

    boolean isMap();

    boolean isEnum();

    /**
     * Returns methods declared by this type. Does not include inherited methods.
     */
    List<MethodAdapter> getMethods();

    /**
     * Returns members declared by this type and inherited from its superclass(es).
     *
     * Currently, "members" means a non-transient non-final non-static fields. In the future "member"
     * is likely to also include a non-static non-abstract setter methods.
     */
    List<MemberAdapter> getAllMembers();

    boolean isSameType(Class<?> type);

    boolean isInterface();

    boolean isLocalClass();

    boolean isAnonymousClass();

    // non-static member classes
    boolean isInnerClass();

    boolean isAbstract();

    /**
     * Returns {@code true} if variable of this {@code type} erasure can be assigned values of
     * {@code other} type erasure.
     */
    boolean isAssignableFrom(Class<?> other);

    boolean isArray();

    boolean hasNoargConstructor();
}
