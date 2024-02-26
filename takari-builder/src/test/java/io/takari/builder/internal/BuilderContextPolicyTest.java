package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderContext.PROPERTY_READ_ACTION;
import static io.takari.builder.internal.BuilderContext.PROPERTY_RW_ACTION;
import static io.takari.builder.internal.BuilderContext.PROPERTY_WRITE_ACTION;
import static org.assertj.core.api.Assertions.assertThat;

import io.takari.builder.enforcer.internal.EnforcerViolationType;
import io.takari.builder.internal.BuilderContext.BuilderContextPolicy;
import io.takari.builder.internal.workspace.FilesystemWorkspace;
import java.io.File;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.helpers.NOPLogger;

/**
 * General tests for the {@link BuilderContext} Policy. More detailed testing for
 * {@link BuilderContext} methods is in {@link BuilderContextTest}
 *
 * @author jaime.morales
 */
public class BuilderContextPolicyTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    private BuilderContext.Builder newBuilder() {
        return BuilderContext.builder(
                NOPLogger.NOP_LOGGER,
                "test",
                temp.getRoot().toPath(),
                null,
                new BuilderWorkspace(new FilesystemWorkspace(), temp.getRoot().toPath(), null));
    }

    @Test
    public void testExecFails() throws Exception {
        BuilderContext ctx = newBuilder().build();

        new BuilderContextPolicy(ctx).checkExec("git");

        assertThat(ctx.getViolations()).hasSize(1);
        assertThat(ctx.getViolations().iterator().next().getViolationType()).isEqualTo(EnforcerViolationType.EXECUTE);
    }

    @Test(expected = SecurityException.class)
    public void testPropertyUnknownAction() throws Exception {
        BuilderContext ctx = newBuilder().build();

        new BuilderContextPolicy(ctx).checkPropertyPermission("non-existent", "property");
    }

    @Test
    public void testPropertyRead() {
        BuilderContext ctx = newBuilder().build();

        new BuilderContextPolicy(ctx).checkPropertyPermission(PROPERTY_READ_ACTION, "property");

        assertThat(ctx.getReadProperties()).containsOnly("property");
    }

    @Test
    public void testPropertyWrite() {
        BuilderContext ctx = newBuilder().build();

        new BuilderContextPolicy(ctx).checkPropertyPermission(PROPERTY_WRITE_ACTION, "property");

        assertThat(ctx.getReadProperties()).isEmpty();
    }

    @Test
    public void testPropertyReadWrite() {
        BuilderContext ctx = newBuilder().build();

        new BuilderContextPolicy(ctx).checkPropertyPermission(PROPERTY_RW_ACTION, "property");

        assertThat(ctx.getReadProperties()).containsOnly("property");
    }

    @Test
    public void testReadExistingFails() throws Exception {
        BuilderContext ctx = newBuilder().build();
        File file = temp.newFile();

        new BuilderContextPolicy(ctx).checkRead(file.getAbsolutePath());

        assertThat(ctx.getViolations()).hasSize(1);
        assertThat(ctx.getViolations().iterator().next().getViolationType()).isEqualTo(EnforcerViolationType.READ);
    }

    @Test(expected = SecurityException.class)
    public void testSocketPermFailure() throws Exception {
        BuilderContext ctx = newBuilder().build();

        new BuilderContextPolicy(ctx).checkSocketPermission();
    }

    @Test
    public void testWriteFails() throws Exception {
        BuilderContext ctx = newBuilder().build();

        new BuilderContextPolicy(ctx).checkWrite("/some/directory");

        assertThat(ctx.getViolations()).hasSize(1);
        assertThat(ctx.getViolations().iterator().next().getViolationType()).isEqualTo(EnforcerViolationType.WRITE);
    }

    @Test
    public void testExecPasses() throws Exception {
        BuilderContext ctx = newBuilder().addExecExceptions(Arrays.asList("p4")).build();

        new BuilderContextPolicy(ctx).checkExec("p4");

        assertThat(ctx.getViolations()).hasSize(0);
    }
}
