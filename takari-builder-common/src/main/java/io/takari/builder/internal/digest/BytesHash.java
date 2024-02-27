/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.digest;

import java.io.Serializable;
import java.util.Arrays;

@SuppressWarnings("serial")
public class BytesHash implements Serializable {

    // no serialVersionUID, want deserialization to fail if state format changes

    private final byte[] bytes;

    public BytesHash(byte[] bytes) {
        this.bytes = bytes;
    }

    // TODO toString

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof BytesHash)) {
            return false;
        }
        return Arrays.equals(bytes, ((BytesHash) obj).bytes);
    }
}
