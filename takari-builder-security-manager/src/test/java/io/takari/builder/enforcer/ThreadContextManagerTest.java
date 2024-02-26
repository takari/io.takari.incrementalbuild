package io.takari.builder.enforcer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ThreadContextManagerTest {

    private Policy p1 = new EmptyPolicy();
    private Policy p2 = new EmptyPolicy();

    private static class GetPolicyContexts implements Callable<Set<Policy>> {
        @Override
        public Set<Policy> call() {
            Map<Object, CachingPolicy> policies = ComposableSecurityManagerPolicy.getContextPolicies();
            return policies != null //
                    ? policies.values().stream()
                            .map(p -> p.policy)
                            .collect(Collectors.toCollection(LinkedHashSet::new)) //
                    : Collections.emptySet();
        }
    }

    @Before
    public void setSystemSecurityManager() {
        ComposableSecurityManagerPolicy.setSystemSecurityManager();
        ComposableSecurityManagerPolicy.registerContextPolicy(new Object(), p1);
        ComposableSecurityManagerPolicy.registerContextPolicy(new Object(), p2);
    }

    @After
    public void removeSystemSecurityManager() {
        ComposableSecurityManagerPolicy.removeSystemSecurityManager();
    }

    @Test
    public void testExecutorService() throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor();

        assertThat(ex.submit(new GetPolicyContexts()).get()) //
                .as("Ensure new thread doesn't have context policies") //
                .isEmpty();

        PolicyContextPreserver preserver = new PolicyContextPreserver();
        assertThat(ex.submit(preserver.wrap(new GetPolicyContexts())).get()) //
                .as("Ensure the preserver preserves policies") //
                .containsOnly(p1, p2);

        assertThat(ex.submit(new GetPolicyContexts()).get()) //
                .as("Ensure no policy context leaked") //
                .isEmpty();
    }

    @Test
    public void testParallelStreams() throws Exception {
        PolicyContextPreserver preserver = new PolicyContextPreserver();
        List<Set<Policy>> parrallelStreamThreadContexts = Arrays.asList(1, 2, 3).parallelStream() //
                .map(i -> preserver.wrap(new GetPolicyContexts()).call()) //
                .collect(Collectors.toList());

        for (Set<Policy> threadPolicies : parrallelStreamThreadContexts) {
            assertThat(threadPolicies) //
                    .as("Ensure all parrallel streams preserved expected policy contexts") //
                    .containsOnly(p1, p2);
        }

        assertThat(new GetPolicyContexts().call()) //
                .as("This threads contexts are unaffected by calls elsewhere") //
                .containsOnly(p1, p2);
    }
}
