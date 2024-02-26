package io.takari.builder.internal.digest;

import static org.assertj.core.api.Assertions.assertThat;

import io.takari.builder.internal.utils.JarBuilder;
import java.io.File;
import java.io.Serializable;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ClasspathDigesterTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testCorruptedZip() throws Exception {
        File jar = temp.newFile().getCanonicalFile();
        Serializable corrupted = new ClasspathDigester().digest(Collections.singletonList(jar.toPath()));
        assertThat(corrupted).isNotNull();

        JarBuilder.create(jar).withEntry("entry", "contents").build();
        Serializable legit = new ClasspathDigester().digest(Collections.singletonList(jar.toPath()));
        assertThat(legit).isNotNull();

        assertThat(corrupted).isNotEqualTo(legit);
    }
}
