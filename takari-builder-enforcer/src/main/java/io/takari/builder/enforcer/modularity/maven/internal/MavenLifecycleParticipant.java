/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.enforcer.modularity.maven.internal;

import io.takari.builder.enforcer.ComposableSecurityManagerPolicy;
import io.takari.builder.enforcer.modularity.internal.DefaultProjectBasedirEnforcer;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;

@Named
@SessionScoped
public class MavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    private final DefaultProjectBasedirEnforcer enforcer;
    private final ModularityEnforcerSessionState state;

    @Inject
    public MavenLifecycleParticipant(DefaultProjectBasedirEnforcer enforcer, ModularityEnforcerSessionState state) {
        this.enforcer = enforcer;
        this.state = state;
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        state.getSessionConfig().logStatus();
        if (state.isEnforcerEnabled()) {
            enforcer.setupMavenSession(session, state.getSessionConfig());
        }
        ComposableSecurityManagerPolicy.setSystemSecurityManager();
    }

    @Override
    public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
        ComposableSecurityManagerPolicy.removeSystemSecurityManager();
    }
}
