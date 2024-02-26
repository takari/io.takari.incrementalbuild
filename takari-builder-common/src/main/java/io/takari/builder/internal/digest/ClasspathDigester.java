/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.digest;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Specialized digester for builder runtime classpath entries. Uses class file contents and immune
 * to file timestamp changes caused by rebuilds of the same sources.
 */
// creative copy&paste from io.takari.incrementalbuild.maven.internal.digest.ClasspathDigester
// TODO cool kids use java8 streams and FileSystems.newFileSystem(jar) these days.
public class ClasspathDigester {

    // individual classpath entry digest cache. assumes that entries do not change during the build.
    // NB: this assumption does not hold inside eclipse and will require different implementation.
    private final ConcurrentMap<String, byte[]> cache;

    /** for testing purposes */
    public ClasspathDigester(ConcurrentMap<String, byte[]> cache) {
        this.cache = cache;
    }

    public ClasspathDigester() {
        this(new ConcurrentHashMap<>());
    }

    public Serializable digest(List<Path> classpath) throws IOException {
        MessageDigest digester = SHA1Digester.newInstance();
        for (Path file : classpath) {
            String cacheKey = file.toFile().getCanonicalPath().toString();
            byte[] hash = cache.get(cacheKey);
            if (hash == null) {
                if (Files.isRegularFile(file)) {
                    hash = digestZip(file);
                } else if (Files.isDirectory(file)) {
                    hash = digestDir(file);
                } else {
                    // does not exist, use token empty array to avoid rechecking
                    hash = new byte[0];
                }
                cache.put(cacheKey, hash);
            }
            digester.update(hash);
        }
        return new BytesHash(digester.digest());
    }

    static byte[] digestDir(Path dir) throws IOException {
        MessageDigest digester = SHA1Digester.newInstance();
        TreeSet<Path> sorted = lsLR(dir, new TreeSet<>());
        for (Path file : sorted) {
            digestFile(digester, file);
        }
        return digester.digest();
    }

    static byte[] digestZip(Path file) throws IOException {
        MessageDigest digester = SHA1Digester.newInstance();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            // sort entries.
            // order of jar/zip entries is not important but may change from one build to the next
            TreeSet<ZipEntry> sorted = new TreeSet<ZipEntry>(new Comparator<ZipEntry>() {
                @Override
                public int compare(ZipEntry o1, ZipEntry o2) {
                    return o2.getName().compareTo(o1.getName());
                }
            });
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                sorted.add(entries.nextElement());
            }
            for (ZipEntry entry : sorted) {
                try (InputStream is = zip.getInputStream(entry)) {
                    digestStream(digester, is);
                }
            }
        } catch (IOException e) {
            // zip file is corrupted or cannot be read, digest as simple file
            digestFile(digester, file);
        }
        return digester.digest();
    }

    private static TreeSet<Path> lsLR(Path directory, TreeSet<Path> sorted) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (Files.isDirectory(file)) {
                    lsLR(file, sorted);
                } else {
                    sorted.add(file);
                }
            }
        } catch (IOException e) {
            return sorted;
        }
        return sorted;
    }

    private static void digestFile(MessageDigest digester, Path file) throws IOException {
        InputStream is = Files.newInputStream(file);
        try {
            digestStream(digester, is);
        } finally {
            is.close();
        }
    }

    private static void digestStream(MessageDigest digester, InputStream is) throws IOException {
        byte[] buf = new byte[4096];
        int r;
        while ((r = is.read(buf)) > 0) {
            digester.update(buf, 0, r);
        }
    }
}
