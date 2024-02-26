/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal;

@SuppressWarnings("serial")
public class ExpressionEvaluationException extends Exception {

    public ExpressionEvaluationException(String message, Exception e) {
        super(message, e);
    }

    public ExpressionEvaluationException(String message) {
        super(message);
    }
}
