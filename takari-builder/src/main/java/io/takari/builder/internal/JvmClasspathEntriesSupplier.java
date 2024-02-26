/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal;

import io.takari.incrementalbuild.classpath.ClasspathEntriesSupplier;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Stream;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class JvmClasspathEntriesSupplier implements ClasspathEntriesSupplier {

    private final Collection<String> classpath = new LinkedHashSet<>();

    public JvmClasspathEntriesSupplier() {
        // these system properties define various class- and native- paths used by jvm and maven core
        // always allow reads from them because classloading can happen at any time

        // java home, needed to read security policy, logging config, etc
        // NB: need to compensate for jre/jdk layout
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            if (javaHome.endsWith(File.separator + "jre")) {
                javaHome = javaHome.substring(0, javaHome.length() - 3);
            }
            classpath.add(javaHome);
        }

        // standard jvm classpath and native path
        addPathProperty("java.endorsed.dirs");
        addPathProperty("java.ext.dirs");
        addPathProperty("java.class.path");
        addPathProperty("java.library.path");
        addPathProperty("sun.boot.library.path"); // classpath

        // maven home and standard (and semi-standard) maven classpath properties
        String mavenHome = System.getProperty("maven.home");
        if (mavenHome != null) {
            classpath.add(mavenHome);
        }
        addPathProperty("maven.ext.class.path");
        addPathProperty("mavendev.testclasspath");
    }

    private void addPathProperty(String name) {
        String value = System.getProperty(name);
        if (value != null && !value.isEmpty()) {
            Stream.of(value.split(File.pathSeparator)) //
                    // dont include empty place or current localtion
                    .filter(path -> path != null && !".".equals(path) && !"".equals(path)) //
                    .forEach(path -> {
                        classpath.add(path);
                    });
        }
    }

    @Override
    public Collection<String> entries() {
        return classpath;
    }
}
