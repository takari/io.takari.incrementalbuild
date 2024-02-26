package io.takari.builder.enforcer.internal;

import static com.google.common.collect.ImmutableList.of;
import static io.takari.builder.enforcer.internal.EnforcerViolationType.EXECUTE;
import static io.takari.builder.enforcer.internal.EnforcerViolationType.READ;
import static io.takari.builder.enforcer.internal.EnforcerViolationType.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

import io.takari.builder.enforcer.internal.EnforcerViolation.Writer;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EnforcerViolationTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testSerialization() throws Exception {
        EnforcerViolation r = new EnforcerViolation(READ, "read-path", of("frame1", "frame2"));
        EnforcerViolation w = new EnforcerViolation(WRITE, "write-path", of());
        EnforcerViolation e = new EnforcerViolation(EXECUTE, "exec-path", of("frame3", "frame4"));

        Path file = temp.newFile().toPath();
        Writer writer = EnforcerViolation.newWriter(file);
        writer.write(r);
        writer.write(w);
        writer.write(e);
        writer.close();

        List<EnforcerViolation> violations = EnforcerViolation.readFrom(file);

        assertThat(violations).containsExactlyInAnyOrder(w, e, r);
    }
}
