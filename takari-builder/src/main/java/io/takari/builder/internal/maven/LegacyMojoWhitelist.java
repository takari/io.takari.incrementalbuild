/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.maven;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.MojoExecutionListener;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;

@Named
@SessionScoped
public class LegacyMojoWhitelist implements MojoExecutionListener {

    static final Charset UTF_8 = Charset.forName("UTF-8");

    public static final String FILE_WHITELIST = ".mvn/mojo-whitelist.config";

    final Path configFile;

    final Map<String, Set<String>> whitelist;

    @Inject
    public LegacyMojoWhitelist(MavenSession session) throws IOException, MojoExecutionException {
        this(
                session.getRequest().getMultiModuleProjectDirectory() != null
                        ? Paths.get(
                                session.getRequest()
                                        .getMultiModuleProjectDirectory()
                                        .getCanonicalPath(),
                                FILE_WHITELIST)
                        : null);
    }

    LegacyMojoWhitelist(Path configFile) throws IOException, MojoExecutionException {
        this.configFile = configFile;
        if (configFile == null) {
            this.whitelist = null;
            return;
        }
        Set<String> whitelist = null;
        Map<String, Set<String>> executionWhitelist = null;
        try (BufferedReader r = Files.newBufferedReader(configFile)) {
            executionWhitelist = new LinkedHashMap<>();
            int lineno = 0;
            String str;
            while ((str = r.readLine()) != null) {
                if (str.startsWith("#")) {
                    // a comment, skip
                    continue;
                }

                str = str.trim();

                if (str.isEmpty()) {
                    // an empty line, skip
                    continue;
                }

                StringTokenizer st = new StringTokenizer(str, ":");
                try {
                    String groupId = st.nextToken();
                    String artifactId = st.nextToken();
                    String goal = st.nextToken();

                    String key = key(groupId, artifactId, goal);

                    if (!executionWhitelist.containsKey(key)) {
                        executionWhitelist.put(key, new LinkedHashSet<>());
                    }

                    if (st.hasMoreTokens()) {
                        String executionId = st.nextToken();
                        String projectGroupId = st.nextToken();
                        String projectArtifactId = st.nextToken();

                        executionWhitelist.get(key).add(executionKey(executionId, projectGroupId, projectArtifactId));

                        if (st.hasMoreElements()) {
                            throw newMojoExecutionException(lineno, str);
                        }
                    }
                } catch (NoSuchElementException e) {
                    throw newMojoExecutionException(lineno, str);
                }
            }
        } catch (FileNotFoundException | NoSuchFileException expected) {
            // this is expected and results in this.whitelist == null
        }
        this.whitelist = executionWhitelist != null ? Collections.unmodifiableMap(executionWhitelist) : null;
    }

    private MojoExecutionException newMojoExecutionException(int lineno, String line) {
        String msg = String.format(
                "Invalid %s:%d configuration, expected <groupId>:<artifactId>:<goal> or <groupId>:<artifactId>"
                        + ":<goal>:<executionId>:<projectGroupId>:<projectArtifactId>, found %s",
                FILE_WHITELIST, lineno, line);
        return new MojoExecutionException(msg);
    }

    private String key(String groupId, String artifactId, String goal) {
        return groupId + ":" + artifactId + ":" + goal;
    }

    private String executionKey(String executionId, String projectGroupId, String projectArtifactId) {
        return executionId + ":" + projectGroupId + ":" + projectArtifactId;
    }

    @Override
    public void beforeMojoExecution(MojoExecutionEvent event) throws MojoExecutionException {
        if (whitelist == null) {
            // enforcement is off by default
            return;
        }

        // note that Maven calls here only for projects that have takari-builder extension
        // enabled either directly or through one of parent projects.

        MojoExecution execution = event.getExecution();

        if (execution.getLifecyclePhase() == null) {
            // don't enforce direct plugin executions
            return;
        }

        String key = key(execution.getGroupId(), execution.getArtifactId(), execution.getGoal());

        final boolean legacy = !AbstractIncrementalMojo.class.isInstance(event.getMojo());
        final boolean executionWhitelisted = isExecutionWhitelisted(
                key,
                execution.getExecutionId(),
                event.getProject().getGroupId(),
                event.getProject().getArtifactId());

        if (legacy && !executionWhitelisted) {
            String msg = String.format(
                    "Unsupported legacy mojo %s @ %s. Whitelist file location %s",
                    execution, event.getProject().getArtifactId(), configFile);
            throw new MojoExecutionException(msg);
        }

        if (!legacy && executionWhitelisted) {
            String msg = String.format(
                    "Redundant whitelist entry for builder %s @ %s. Whitelist file location %s",
                    execution, event.getProject().getArtifactId(), configFile);
            throw new MojoExecutionException(msg);
        }
    }

    boolean isExecutionWhitelisted(String key, String executionId, String projectGroupId, String projectArtifactId) {

        if (!whitelist.containsKey(key)) {
            // not whitelisted
            return false;
        }

        Set<String> executions = whitelist.get(key);

        if (executions.isEmpty()) {
            // no specific executions whitelisted. Allow
            return true;
        }

        return executions.contains(executionKey(executionId, projectGroupId, projectArtifactId)) // explicitly
                // whitelisted
                // executions
                || executions.contains(executionKey(executionId, "*", "*")); // wildcard whitelisted
        // execution
    }

    @Override
    public void afterMojoExecutionSuccess(MojoExecutionEvent event) throws MojoExecutionException {}

    @Override
    public void afterExecutionFailure(MojoExecutionEvent event) {}
}
