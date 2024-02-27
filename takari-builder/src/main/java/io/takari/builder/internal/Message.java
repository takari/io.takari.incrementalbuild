/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
// copy&paste from io.takari.incrementalbuild.spi.Message
package io.takari.builder.internal;

import java.io.Serializable;

@SuppressWarnings("serial")
public class Message implements Serializable {

    // no serialVersionUID, want deserialization to fail if state format changes

    public static enum MessageSeverity {
        ERROR,
        WARNING,
        INFO
    }

    public final String file;

    public final int line;

    public final int column;

    public final String message;

    public final MessageSeverity severity;

    public final Throwable cause;

    private final int hashCode;

    public Message(String file, int line, int column, String message, MessageSeverity severity, Throwable cause) {
        this.file = file;
        this.line = line;
        this.column = column;
        this.message = message;
        this.severity = severity;
        this.cause = cause;
        this.hashCode = _hashCode();
    }

    private int _hashCode() {
        int result = 31;
        result = result * 17 + file.hashCode();
        result = result * 17 + line;
        result = result * 17 + column;
        result = result * 17 + (message != null ? message.hashCode() : 0);
        result = result * 17 + (severity != null ? severity.hashCode() : 0);
        result = result * 17 + (cause != null ? cause.hashCode() : 0);
        return result;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Message)) {
            return false;
        }
        Message other = (Message) obj;
        return line == other.line
                && column == other.column
                && eq(file, other.file)
                && eq(message, other.message)
                && eq(severity, other.severity)
                && eq(cause, other.cause);
    }

    private static <T> boolean eq(T a, T b) {
        return a != null ? a.equals(b) : b == null;
    }
}
