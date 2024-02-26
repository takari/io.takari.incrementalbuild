/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild;

import java.io.File;

public interface Resource<T> extends ResourceMetadata<T> {

    public void addMessage(int line, int column, String message, MessageSeverity severity, Throwable cause);

    public Output<File> associateOutput(Output<File> output);

    public Output<File> associateOutput(File outputFile);
}
