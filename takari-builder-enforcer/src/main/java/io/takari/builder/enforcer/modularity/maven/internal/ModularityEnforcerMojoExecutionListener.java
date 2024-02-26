/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.enforcer.modularity.maven.internal;

import io.takari.builder.enforcer.modularity.internal.DefaultProjectBasedirEnforcer;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.MojoExecutionListener;

@Named
@SessionScoped
public class ModularityEnforcerMojoExecutionListener implements MojoExecutionListener {

    private final DefaultProjectBasedirEnforcer enforcer;
    private final ModularityEnforcerSessionState state;

    @Inject
    public ModularityEnforcerMojoExecutionListener(
            DefaultProjectBasedirEnforcer enforcer, ModularityEnforcerSessionState state) {
        this.enforcer = enforcer;
        this.state = state;
    }

    @Override
    public void beforeMojoExecution(MojoExecutionEvent event) {
        if (enforce(event)) {
            enforcer.setupProjectContext(
                    event.getSession(), event.getProject(), state.getSessionConfig(), state.getEnforcerConfig());
        }
    }

    @Override
    public void afterMojoExecutionSuccess(MojoExecutionEvent event) {
        if (enforce(event)) {
            enforcer.finishProjectContext(state.getTopleveldir(), event.getProject(), state.getSessionConfig());
        }
    }

    @Override
    public void afterExecutionFailure(MojoExecutionEvent event) {
        if (enforce(event)) {
            enforcer.finishProjectContext(state.getTopleveldir(), event.getProject(), state.getSessionConfig());
        }
    }

    private boolean enforce(MojoExecutionEvent event) {
        // not enforcing modularity if this mojo execution is not part of the maven lifecycle or if
        // being invoked from m2e
        return state.isEnforcerEnabled()
                && event.getExecution().getLifecyclePhase() != null
                && event.getSession().getRequest().getUserProperties().get("m2e.version") == null;
    }
}
