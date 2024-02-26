/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.enforcer;

import java.io.FilePermission;

public class ExecCommandPassingSecurityManager extends SecurityManager {

    /**
     * The default implementation of the security manager treats calls to execute non-absolute file
     * paths as <<ALL FILES>> rather than the single command. This implementation is more useful to
     * us.
     */
    @Override
    public void checkExec(String cmd) {
        checkPermission(new FilePermission(cmd, "execute"));
    }

    @Override
    public void checkRead(String file) {
        checkPermission(SimpleFilePermission.createFileRead(file));
    }

    @Override
    public void checkRead(String file, Object context) {
        checkPermission(SimpleFilePermission.createFileRead(file), context);
    }

    @Override
    public void checkWrite(String file) {
        checkPermission(SimpleFilePermission.createFileWrite(file));
    }

    @Override
    public void checkDelete(String file) {
        checkPermission(SimpleFilePermission.createFileWrite(file));
    }
}
