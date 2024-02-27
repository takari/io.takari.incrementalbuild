/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder;

public enum LifecyclePhase {
    // apt-processor assumes names match org.apache.maven.plugins.annotations.LifecyclePhase

    NONE,

    VALIDATE,
    INITIALIZE,
    GENERATE_SOURCES,
    PROCESS_SOURCES,
    GENERATE_RESOURCES,
    PROCESS_RESOURCES,
    COMPILE,
    PROCESS_CLASSES,
    GENERATE_TEST_SOURCES,
    PROCESS_TEST_SOURCES,
    GENERATE_TEST_RESOURCES,
    PROCESS_TEST_RESOURCES,
    TEST_COMPILE,
    PROCESS_TEST_CLASSES,
    TEST,
    PREPARE_PACKAGE,
    PACKAGE,
    PRE_INTEGRATION_TEST,
    INTEGRATION_TEST,
    POST_INTEGRATION_TEST,
    VERIFY,
    INSTALL,
    DEPLOY,

    PRE_CLEAN,
    CLEAN,
    POST_CLEAN,

    PRE_SITE,
    SITE,
    POST_SITE,
    SITE_DEPLOY;
}
