package io.takari.builder.enforcer;

import static io.takari.builder.enforcer.ComposableSecurityManagerPolicy.getContextPolicies;
import static io.takari.builder.enforcer.ComposableSecurityManagerPolicy.setContextPolicies;

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.MDC;

/**
 * By constructing a {@link PolicyContextPreserver} the contents of the current {@link Policy} associated with the
 * {@link ComposableSecurityManager} will be stored in this instance.   
 * 
 * The single instance is reusable to wrap as many {@link Callable}s or {@link Runnable}s as needed for parallel streams
 * or work to be passed to an {@link java.util.concurrent.Executor} or a newly constructed {@link java.lang.Thread}.
 * 
 */
public class PolicyContextPreserver {

  private final Map<Object, CachingPolicy> policies;

  @SuppressWarnings("rawtypes")
  private final Map mdc;
  
  public PolicyContextPreserver() {
     Map<Object, CachingPolicy> policies = getContextPolicies();
     this.policies = policies;
     mdc = getMDC();
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
        Map<Object, CachingPolicy> _policies = getContextPolicies();
        @SuppressWarnings("rawtypes")
        final Map _mdc = getMDC();
        try {
          setMDC(mdc);
          setContextPolicies(policies);
          runnable.run();
        } finally {
          setContextPolicies(_policies);
          setMDC(_mdc);
        }
      }
    };
  }

  public <T> WrappedCallable<T> wrap(Callable<T> callable) {
    return new WrappedCallable<T>() {
      @Override
      public T call() throws WrappedException {
        Map<Object, CachingPolicy> _policies = getContextPolicies();
        @SuppressWarnings("rawtypes")
        final Map _mdc = getMDC();
        try {
          setMDC(mdc);
          setContextPolicies(policies);
          return callable.call();
        } catch (RuntimeException e) {
          throw e;
        } catch (Exception e) {
          String message = "Callable " + callable.getClass().getName() + " failed with execption "
              + e.getClass().getName();
          throw new WrappedException(message, e);
        } finally {
          setContextPolicies(_policies);
          setMDC(_mdc);
        }
      }
    };
  }

  @SuppressWarnings("rawtypes")
  protected static Map getMDC() {
    return MDC.getCopyOfContextMap();
  }

  @SuppressWarnings("rawtypes")
  protected static void setMDC(final Map mdc) {
    if (mdc != null) {
      MDC.setContextMap(mdc);
    } else {
      MDC.clear();
    }
  }
}
