/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.enforcer;

public interface Policy {

    public void checkSocketPermission();

    public void checkPropertyPermission(String action, String name);

    public void checkExec(String cmd);

    public void checkRead(String file);

    public void checkWrite(String file);
}
