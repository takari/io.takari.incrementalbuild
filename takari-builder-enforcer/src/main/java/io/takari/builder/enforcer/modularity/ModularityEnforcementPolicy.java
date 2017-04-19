package io.takari.builder.enforcer.modularity;

import static io.takari.builder.internal.pathmatcher.PathNormalizer.normalize0;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import io.takari.builder.enforcer.Policy;
import io.takari.builder.enforcer.internal.EnforcerViolation;
import io.takari.builder.enforcer.internal.EnforcerViolationType;

/**
 * This is the heart of this project.
 * <p/>
 * This is a security manager used to prevent file system access from within the jvm to any resource outside of a maven
 * project's directory hierarchy, and to prevent writing any files, except to the the project's output directory.
 * <p/>
 * This constraints are loosened to support the proper execution of maven. You should be able to read your dependencies,
 * for example.
 * <p/>
 * In order to handle multiple reactor builds occurring at the same time within the same jvm (takari-smart-builder) the
 * configuration of files allowed to be accessed during execution are stored in an {@link InheritableThreadLocal}
 * instance of {@link ProjectContext}.
 * <p/>
 * If you run code generators, such as apt during the generate-sources phase, you should be able to access your
 * dependencies sources dirs in a reactor build. In general, you should follow the standard takari life cycle and
 * generate sources during the compilation phase, so that your source should not need to rely on any source code from
 * another project, instead rely only on it's compiled sources and copied resources.
 * <p/>
 * If you need to generate sources during the generate-sources phase with an apt implementation, or carefully list your
 * code generators to occur before compilation, but still in the compilation phase.
 * <p/>
 * If you have code/resources generators that must be interleaved in the compilation phase to generate source from apt
 * sources, and you are not using apt to do this (it handles multiple pass code generation -- though might lead to an
 * infinite loop), you are a terrible person and your use case is not supported
 *
 * @author rex.hoffman (just the javadoc)
 * @author Igor Fedorenko
 */
public class ModularityEnforcementPolicy implements Policy {

  @Override
  public void checkRead(String file) {
    checkScope();
    checkProjectRead(file);
  }

  @Override
  public void checkWrite(String file) {
    checkScope();
    checkProjectWrite(file);
  }

  @Override
  public void checkExec(String cmd) {
    checkScope();
    if (execPrivileged.get() != null) { return; }
    if (!context.checkExecute(cmd)) {
      handler.accept(context, new EnforcerViolation(EnforcerViolationType.EXECUTE, cmd));
    }
  }

  @Override
  public void checkSocketPermission() {
    checkScope();
    // allow all
  }

  @Override
  public void checkPropertyPermission(String action, String name) {
    checkScope();
    // allow all
  }

  //
  //
  //

  private final ProjectContext context;

  private final BiConsumer<ProjectContext, EnforcerViolation> handler;

  private final ThreadLocal<Boolean> execPrivileged = new ThreadLocal<>();

  private final AtomicBoolean inScope = new AtomicBoolean(true);

  public ModularityEnforcementPolicy(ProjectContext context, BiConsumer<ProjectContext, EnforcerViolation> handler) {
    this.context = context;
    this.handler = handler;
  }

  public void checkProjectRead(String file) {
    if (!context.checkRead(file)) {
      handler.accept(context, new EnforcerViolation(EnforcerViolationType.READ, normalize0(file)));
    }
  }

  public void checkProjectWrite(String file) {
    if (!context.checkWrite(file)) {
      handler.accept(context, new EnforcerViolation(EnforcerViolationType.WRITE, normalize0(file)));
    }
  }

  public void enterExecPrivileged() {
    execPrivileged.set(Boolean.TRUE);
  }

  public void leaveExecPrivileged() {
    execPrivileged.remove();
  }

  public ProjectContext getProjectContext() {
    return context;
  }

  private void checkScope() {
    if (!inScope.get()) { throw new IllegalStateException("ModularityEnforcerContext is no longer in scope"); }
  }

  public void close() {
    inScope.set(false);
  }
}
