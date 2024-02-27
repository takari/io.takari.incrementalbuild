/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Use this annotation if your builder needs to access resources outside of declared inputs. This is
 * not safe, and will result in unexpected and hard-to-debug errors, but it may be needed due to
 * strange and evil things being done in build steps (such as checking generated files into source
 * control)
 *
 * In addition to using this annotation, you must add an entry into the white-list file
 * (${multimodule-project-root}/.mvn/builder-enforcer.config)
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface NonDeterministic {}
