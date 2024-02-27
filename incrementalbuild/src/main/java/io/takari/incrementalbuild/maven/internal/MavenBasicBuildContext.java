/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.incrementalbuild.maven.internal;

import io.takari.incrementalbuild.BasicBuildContext;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.incrementalbuild.spi.BuildContextEnvironment;
import io.takari.incrementalbuild.spi.DefaultBasicBuildContext;
import java.io.File;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import org.apache.maven.execution.scope.MojoExecutionScoped;

@Named
public class MavenBasicBuildContext implements BasicBuildContext {

    @Named
    @Typed(MojoExecutionScopedBasicBuildContext.class)
    @MojoExecutionScoped
    public static class MojoExecutionScopedBasicBuildContext extends DefaultBasicBuildContext {
        @Inject
        public MojoExecutionScopedBasicBuildContext(BuildContextEnvironment configuration) {
            super(configuration);
        }
    }

    private final Provider<MojoExecutionScopedBasicBuildContext> provider;

    @Inject
    public MavenBasicBuildContext(Provider<MojoExecutionScopedBasicBuildContext> provider) {
        this.provider = provider;
    }

    @Override
    public ResourceMetadata<File> registerInput(File inputFile) {
        return provider.get().registerInput(inputFile);
    }

    @Override
    public boolean isProcessingRequired() {
        return provider.get().isProcessingRequired();
    }

    @Override
    public Output<File> processOutput(File outputFile) {
        return provider.get().processOutput(outputFile);
    }
}
