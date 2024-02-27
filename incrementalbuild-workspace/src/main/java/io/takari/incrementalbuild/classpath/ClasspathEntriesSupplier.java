/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.classpath;

import java.util.Collection;

/**
 *
 * Supplies a list of classpath entries to be allowed read-access from takari-builder framework
 *
 * @author jaime.morales
 */
public interface ClasspathEntriesSupplier {

    public Collection<String> entries();
}
