/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.enforcer.modularity.maven;

import io.takari.builder.enforcer.internal.EnforcerConfig;
import java.io.File;
import java.io.IOException;

public interface ProjectBasedirEnforcer {

    void writeConfiguration(File file) throws IOException;

    void replayLog(File file) throws IOException;

    void enterExecPrivileged();

    void leaveExecPrivileged();

    boolean isEnabledForProject(EnforcerConfig config, String artifactId);
}
