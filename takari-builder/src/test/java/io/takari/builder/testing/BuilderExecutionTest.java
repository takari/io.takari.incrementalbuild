package io.takari.builder.testing;

import static io.takari.builder.testing.BuilderExecution.builderExecution;
import static io.takari.builder.testing.BuilderExecution.newArtifactMetadata;
import static io.takari.builder.testing.BuilderExecution.newArtifactResources;
import static org.assertj.core.api.Assertions.assertThat;

import io.takari.builder.Builder;
import io.takari.builder.Dependencies;
import io.takari.builder.DependencyResources;
import io.takari.builder.IArtifactMetadata;
import io.takari.builder.IArtifactResources;
import io.takari.builder.Parameter;
import io.takari.builder.ResolutionScope;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BuilderExecutionTest {

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    static class _ArtifactMetadataBuilder {
        @Dependencies(scope = ResolutionScope.COMPILE)
        List<IArtifactMetadata> dependencies;

        @Builder(name = "test")
        public void execute() {
            assertThat(dependencies).hasSize(1);
        }
    }

    @Test
    public void testDependencies_withParameter() throws Exception {
        builderExecution(temp.newFolder(), _ArtifactMetadataBuilder.class) //
                .withParameterValue("dependencies", Arrays.asList(newArtifactMetadata("g:a:1"))) //
                .execute();
    }

    @Test
    public void testDependencies_withDependency() throws Exception {
        builderExecution(temp.newFolder(), _ArtifactMetadataBuilder.class) //
                .withDependency("g:a:1", temp.newFile()) //
                .execute();
    }

    //
    //
    //

    static class _TransitiveArtifactMetadataBuilder {
        @Dependencies(scope = ResolutionScope.COMPILE)
        List<IArtifactMetadata> dependencies;

        @Dependencies(scope = ResolutionScope.COMPILE, transitive = false)
        List<IArtifactMetadata> directDependencies;

        @Builder(name = "test")
        public void execute() {
            assertThat(dependencies).hasSize(1);
            assertThat(directDependencies).isNull();
        }
    }

    @Test
    public void testDependencies_withTransitiveDependency() throws Exception {
        builderExecution(temp.newFolder(), _TransitiveArtifactMetadataBuilder.class) //
                .withTransitiveDependency("gt:at:1", temp.newFile()) //
                .execute();
    }

    //
    //
    //

    static class _ArtifactResourcesBuilder {
        @DependencyResources(scope = ResolutionScope.COMPILE, includes = "**/*")
        List<IArtifactResources> dependencies;

        @Builder(name = "test")
        public void execute() {
            assertThat(dependencies).hasSize(1);
        }
    }

    @Test
    public void testDependencyResources_withParameter() throws Exception {
        URL url = temp.newFile().toURI().toURL();
        builderExecution(temp.newFolder(), _ArtifactResourcesBuilder.class) //
                .withParameterValue("dependencies", Arrays.asList(newArtifactResources("g:a:1", url))) //
                .execute();
    }

    @Test
    public void testDependencyResources_withDependency() throws Exception {
        File dep = temp.newFolder();
        new File(dep, "a.txt").createNewFile();
        builderExecution(temp.newFolder(), _ArtifactResourcesBuilder.class) //
                .withDependency("g:a:1", dep) //
                .execute();
    }

    //
    //
    //

    static class _SimpleBuilder {
        @Parameter(required = false)
        String parameter;

        @Builder(name = "test")
        public void execute() {}
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithUnknownParameter() throws Exception {
        // BuilderExecution must not silently ignore unknown parameters
        builderExecution(temp.newFolder(), _SimpleBuilder.class) //
                .withParameterValue("unknown", "value") //
                .execute();
    }
}
