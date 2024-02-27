/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.spi;

import java.util.Collection;
import java.util.Map;

public interface MessageSinkAdaptor {

    void clear(Object resource);

    void record(Map<Object, Collection<Message>> allMessages, Map<Object, Collection<Message>> newMessages);
}
