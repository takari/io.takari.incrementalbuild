/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal;

import org.codehaus.plexus.util.xml.Xpp3Dom;

public class Dependency {

    private final String groupId;
    private final String artifactId;
    private final String classifier;

    private static boolean matchesClassifier(String c1, String c2) {
        return (c1 == null || c1.isEmpty() || c1.equals(c2));
    }

    public static Dependency fromString(String str) {
        String[] sections = str.split(":");
        String groupId = sections[0];
        String artifactId = sections[1];
        String classifier = sections.length > 2 ? sections[2] : null;

        return new Dependency(groupId, artifactId, classifier);
    }

    static Dependency fromConfig(Xpp3Dom config) {
        Xpp3Dom groupId = config.getChild("groupId");
        Xpp3Dom artifactId = config.getChild("artifactId");
        Xpp3Dom classifier = config.getChild("classifier");

        return new Dependency(
                groupId.getValue(), artifactId.getValue(), classifier != null ? classifier.getValue() : null);
    }

    public Dependency(String groupId, String artifactId, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classifier = classifier;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getClassifier() {
        return classifier;
    }

    /**
     * Return true if the artifact matches the specified dependency. If the classifier is empty /
     * undefined, return the first matching artifact. If the classifier is defined, return only an
     * artifact matching the specified classifier.
     **/
    public boolean matchesArtifact(String groupId, String artifactId, String version, String classifier) {
        return (getGroupId().equals(groupId)
                && getArtifactId().equals(artifactId)
                && matchesClassifier(getClassifier(), classifier));
    }

    @Override
    public String toString() {
        return (getGroupId() + ":" + getArtifactId()
                + ((getClassifier() == null || getClassifier().isEmpty()) ? "" : (":" + getClassifier())));
    }
}
