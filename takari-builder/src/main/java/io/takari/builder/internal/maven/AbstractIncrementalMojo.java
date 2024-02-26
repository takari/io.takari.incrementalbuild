/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.internal.maven;

import static io.takari.builder.internal.maven.Takari_MavenIncrementalConventions.getExecutionStateLocation;

import io.takari.builder.ResourceType;
import io.takari.builder.enforcer.internal.EnforcerConfig;
import io.takari.builder.enforcer.modularity.internal.WorkspaceProjectsProvider.Nullable;
import io.takari.builder.internal.BuilderRunner;
import io.takari.builder.internal.ClasspathMatcher;
import io.takari.builder.internal.ResourceRoot;
import io.takari.builder.internal.resolver.ArtifactResolverProvider;
import io.takari.incrementalbuild.workspace.MessageSink;
import io.takari.incrementalbuild.workspace.Workspace;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractIncrementalMojo extends AbstractMojo {

    private static final String BUILDER_ARTIFACTID = "takari-builder";
    private static final String BUILDER_GROUPID = "io.takari.builder";
    private static final String ENFORCER_CONFIG_FILE_LOCATION = ".mvn/builder-enforcer.config";

    protected final Class<?> builderType;
    protected final Logger log;

    @Inject
    private ClasspathMatcher classpathMatcher;

    @Inject
    private Workspace workspace;

    @Inject
    @Nullable
    private MessageSink messageSink;

    @Inject
    @Named(MavenArtifactResolverProvider.MAVEN)
    private ArtifactResolverProvider<MavenProject> resolverProvider;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject __internal_project;

    @Parameter(defaultValue = "${mojo}", readonly = true)
    private MojoExecution __internal_execution;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession __internal_session;

    protected AbstractIncrementalMojo(Class<?> builderType) {
        this.builderType = builderType;
        this.log = LoggerFactory.getLogger(builderType);
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        List<Artifact> classpath =
                __internal_execution.getMojoDescriptor().getPluginDescriptor().getArtifacts();

        assertBuildExtensionRealm(classpath);

        Consumer<ResourceRoot> resourceConsumer = resourceRoot -> {
            Resource resource = new Resource();
            resource.setDirectory(resourceRoot.getLocation());
            resource.setIncludes(resourceRoot.getIncludes());
            resource.setExcludes(resourceRoot.getExcludes());

            ResourceType type = resourceRoot.getResourceType();
            if (type.equals(ResourceType.MAIN)) {
                __internal_project.getResources().add(resource);
            } else if (type.equals(ResourceType.TEST)) {
                __internal_project.getTestResources().add(resource);
            }
        };

        EnforcerConfig enforcerConfig = getEnforcerConfig();

        MavenClasspathDigester classpathDigester = new MavenClasspathDigester(__internal_session);

        BuilderRunner.create(log, builderType, __internal_execution.getGoal()) //
                .setBuilderId(getBuilderId()) //
                .setSessionBasedir(getSessionBasedir()) //
                .setProjectBasedir(__internal_project.getBasedir().toPath()) //
                .setProjectProperties(new MavenProjectPropertyResolver(__internal_project)) //
                .setSessionProperties(
                        __internal_session.getSystemProperties(), __internal_session.getUserProperties()) //
                .setSessionClasspathMatcher(classpathMatcher.getMatcher()) //
                .setStateFile(getExecutionStateLocation(__internal_project, __internal_execution)) //
                .setClasspath(
                        classpath.stream().map(a -> a.getFile().toPath()).collect(Collectors.toList()),
                        classpathDigester) //
                .setDependencyResolver(resolverProvider.getResolver(__internal_project)) //
                .setProjectResourcesConsumer(resourceConsumer) //
                .setProjectCompileSourceRoots(__internal_project.getCompileSourceRoots()) //
                .setProjectTestCompileSourceRoots(__internal_project.getTestCompileSourceRoots()) //
                .setConfiguration(__internal_execution.getConfiguration()) //
                .setDefaultMessageLocation(__internal_project.getFile().toPath(), -1, -1) //
                .setBuilderEnforcerConfig(enforcerConfig) //
                .setWorkspace(workspace) //
                .setMessageSink(messageSink) //
                .execute((m, c) -> c != null ? new MojoExecutionException(m, c) : new MojoExecutionException(m));
    }

    private Path getSessionBasedir() {
        if (__internal_session.getRequest().getMultiModuleProjectDirectory() == null) {
            return null;
        }
        return __internal_session.getRequest().getMultiModuleProjectDirectory().toPath();
    }

    private EnforcerConfig getEnforcerConfig() {
        return EnforcerConfig.fromFile(
                getSessionBasedir() == null ? null : getSessionBasedir().resolve(ENFORCER_CONFIG_FILE_LOCATION));
    }

    private String getBuilderId() {
        PluginDescriptor descriptor = __internal_execution.getMojoDescriptor().getPluginDescriptor();
        return String.format(
                "%s:%s:%s", descriptor.getGroupId(), descriptor.getArtifactId(), __internal_execution.getGoal());
    }

    private void assertBuildExtensionRealm(List<Artifact> classpath) throws MojoExecutionException {
        // there is no direct way to assert that Builder Framework is loaded as a build extension
        // as an approximation, check that plugin classpath does not include takari-builder artifact

        boolean present = classpath.stream() //
                .anyMatch(a -> BUILDER_GROUPID.equals(a.getGroupId()) && BUILDER_ARTIFACTID.equals(a.getArtifactId()));

        if (present) {
            String msg =
                    String.format("%s:%s must be configured as a build extension", BUILDER_GROUPID, BUILDER_ARTIFACTID);
            throw new MojoExecutionException(msg);
        }
    }
}
