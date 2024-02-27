/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.enforcer.internal;

public enum EnforcerViolationType {
    READ("R"),
    WRITE("W"),
    EXECUTE("E");

    private final String type;

    private EnforcerViolationType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static EnforcerViolationType fromType(String type) {
        for (EnforcerViolationType value : values()) {
            if (type.equals(value.getType())) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid enforcer violation type: " + type);
    }
}
