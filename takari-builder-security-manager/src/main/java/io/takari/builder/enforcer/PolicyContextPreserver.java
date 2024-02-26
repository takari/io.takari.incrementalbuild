/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.builder.enforcer;

import static io.takari.builder.enforcer.ComposableSecurityManagerPolicy.getContextPolicies;
import static io.takari.builder.enforcer.ComposableSecurityManagerPolicy.setContextPolicies;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.MDC;

/**
 * By constructing a {@link PolicyContextPreserver} the contents of the current {@link Policy}
 * associated with the {@link ComposableSecurityManager} will be stored in this instance.
 *
 * The single instance is reusable to wrap as many {@link Callable}s or {@link Runnable}s as needed
 * for parallel streams or work to be passed to an {@link java.util.concurrent.Executor} or a newly
 * constructed {@link java.lang.Thread}.
 *
 */
public class PolicyContextPreserver {

    /**
     * Represents thread context and provides methods to preserve and restore the context value.
     *
     * @see PolicyContextPreserver#registerAccessor(CurrentContextAccessor)
     */
    public static interface CurrentContextAccessor {

        /**
         * Returns current thread context value.
         */
        public Object getCurrentContext();

        /**
         * Sets the current thread context value.
         */
        public void setCurrentContext(Object value);
    }

    public static void registerAccessor(CurrentContextAccessor accessor) {
        accessors.add(accessor);
    }

    private static final List<CurrentContextAccessor> accessors =
            new CopyOnWriteArrayList<>(new CurrentContextAccessor[] {new PolicyPreserver(), new SLF4JPreserver()});

    private static IdentityHashMap<CurrentContextAccessor, Object> preserve() {
        IdentityHashMap<CurrentContextAccessor, Object> context = new IdentityHashMap<>();
        accessors.forEach(d -> context.put(d, d.getCurrentContext()));
        return context;
    }

    private static void restore(IdentityHashMap<CurrentContextAccessor, Object> context) {
        context.forEach((p, v) -> p.setCurrentContext(v));
    }

    private static class SLF4JPreserver implements CurrentContextAccessor {
        @Override
        public Map getCurrentContext() {
            return MDC.getCopyOfContextMap();
        }

        @Override
        public void setCurrentContext(Object value) {
            Map mdc = (Map) value;
            if (mdc != null) {
                MDC.setContextMap(mdc);
            } else {
                MDC.clear();
            }
        }
    }

    private static class PolicyPreserver implements CurrentContextAccessor {

        @Override
        public Map<Object, CachingPolicy> getCurrentContext() {
            return getContextPolicies();
        }

        @Override
        public void setCurrentContext(Object value) {
            @SuppressWarnings("unchecked")
            Map<Object, CachingPolicy> policies = (Map<Object, CachingPolicy>) value;
            setContextPolicies(policies);
        }
    }

    private final IdentityHashMap<CurrentContextAccessor, Object> preservedContext;

    public PolicyContextPreserver() {
        this.preservedContext = preserve();
    }

    @SuppressWarnings("serial")
    public static class WrappedException extends RuntimeException {

        public WrappedException(String message, Exception cause) {
            super(message, cause);
        }
    }

    public static interface WrappedCallable<T> extends Callable<T> {
        @Override
        T call() throws WrappedException;
    }

    public Runnable wrap(final Runnable runnable) {
        return new Runnable() {
            @Override
            public void run() {
                IdentityHashMap<CurrentContextAccessor, Object> threadContext = preserve();
                try {
                    restore(preservedContext);
                    runnable.run();
                } finally {
                    restore(threadContext);
                }
            }
        };
    }

    public <T> WrappedCallable<T> wrap(Callable<T> callable) {
        return new WrappedCallable<T>() {
            @Override
            public T call() throws WrappedException {
                IdentityHashMap<CurrentContextAccessor, Object> threadContext = preserve();
                try {
                    restore(preservedContext);
                    return callable.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    String message = "Callable " + callable.getClass().getName() + " failed with execption "
                            + e.getClass().getName();
                    throw new WrappedException(message, e);
                } finally {
                    restore(threadContext);
                }
            }
        };
    }
}
