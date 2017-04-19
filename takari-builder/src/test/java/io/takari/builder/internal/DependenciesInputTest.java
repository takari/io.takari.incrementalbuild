package io.takari.builder.internal;

import static io.takari.builder.internal.BuilderInputs.digest;
import static io.takari.builder.internal.TestInputBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.builder.Dependencies;
import io.takari.builder.IArtifactMetadata;
import io.takari.builder.ResolutionScope;
import io.takari.builder.internal.BuilderInputs.DependencyValue;
import io.takari.builder.internal.BuilderInputs.Digest;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidConfigurationException;
import io.takari.builder.internal.BuilderInputsBuilder.InvalidModelException;
import io.takari.builder.internal.TestInputBuilder.TestArtifactMetadata;

public class DependenciesInputTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private IArtifactMetadata newArtifact(String gac) {
    return new TestArtifactMetadata(gac);
  }
  
  @Test
  public void testDigest() throws Exception {
    Path path = temp.newFile().toPath();
    Files.write(path, "content".getBytes());

    Digest digest = digest(new DependencyValue(File.class, newArtifact("g:a"), path));
    Digest metadataDigest = digest(new DependencyValue(IArtifactMetadata.class, newArtifact("g:a"), path));

    assertThat(digest) //
        .isEqualTo(digest(new DependencyValue(File.class, newArtifact("g:a"), path)));

    assertThat(digest) //
        .as("same location, same artifact, IArtifactMetadata") //
        .isNotEqualTo(metadataDigest);

    assertThat(digest) //
        .as("same location, different artifact") //
        .isNotEqualTo(digest(new DependencyValue(File.class, newArtifact("g:b"), path)));

    Path htap = temp.newFile().toPath();
    assertThat(digest) //
        .as("same artifact, different location") //
        .isNotEqualTo(digest(new DependencyValue(File.class, newArtifact("g:a"), htap)));

    Files.write(path, "changed-content".getBytes());
    assertThat(digest) //
        .as("same artifact, same location, different content") //
        .isNotEqualTo(digest(new DependencyValue(File.class, newArtifact("g:a"), path)));

    assertThat(metadataDigest) //
        .as("same artifact, same location, different content, IArtifactMetadata") //
        .isEqualTo(digest(new DependencyValue(IArtifactMetadata.class, newArtifact("g:a"), path)));
  }

  //
  //
  //

  static class _LocationData {
    @Dependencies(scope = ResolutionScope.COMPILE)
    public List<File> dependencies;
  }

  @Test
  public void testDependenciesInput() throws Exception {
    String dependency1 = "dependency1";
    String dependency2 = "dependency2";
    
    File depFile1 = temp.newFile(dependency1).getCanonicalFile();
    File depFile2 = temp.newFile(dependency2).getCanonicalFile();
    
    @SuppressWarnings("unchecked")
    List<File> input = (List<File>) builder() //
        .withDependencies(depFile1.getCanonicalPath(), depFile2.getCanonicalPath()) //
        .build(_LocationData.class, "dependencies").value();
    assertThat(input).containsExactly(depFile1, depFile2);
  }

  @Test(expected = InvalidConfigurationException.class)
  public void testFailureOnConfiguration() throws Exception {
    builder() //
        .withConfigurationXml("<dependencies>TEST</dependencies>")
        .build(_LocationData.class, "dependencies");
  }

  @Test
  public void testEmptyDependencies() throws Exception {
    assertNull(builder().build(_LocationData.class, "dependencies"));
  }

  @Test
  public void testExplodedDependency() throws Exception {
    File dep = temp.newFolder();
    Files.write(new File(dep, "entry.txt").toPath(), "content".getBytes());
    BuilderInputs inputs = builder().withDependency("g:a", dep).build(_LocationData.class);
    inputs.inputFiles.forEach(f -> assertThat(f).hasParent(dep.toPath()));
    Digest digest = inputs.getDigest();

    Files.write(new File(dep, "entry.txt").toPath(), "changed-content".getBytes());
    assertThat(digest) //
        .isNotEqualTo(builder().withDependency("g:a", dep).build(_LocationData.class).getDigest());
  }

  //
  //
  //

  static class _PathLocationData {
    @Dependencies(scope = ResolutionScope.COMPILE)
    public List<Path> dependencies;
  }

  @Test
  public void testPathDependenciesInput() throws Exception {
    String dependency1 = "dependency1";
    String dependency2 = "dependency2";
    File depFile1 = temp.newFile(dependency1).getCanonicalFile();
    File depFile2 = temp.newFile(dependency2).getCanonicalFile();
    
    @SuppressWarnings("unchecked")
    List<Path> input = (List<Path>) builder() //
        .withDependencies(depFile1.getCanonicalPath(), depFile2.getCanonicalPath()) //
        .build(_PathLocationData.class, "dependencies").value();
    assertThat(input).containsExactly(depFile1.toPath(), depFile2.toPath());
  }

  @Test
  public void testPathDependencyInput_directory() throws Exception {
    File dirDep = temp.newFolder().getCanonicalFile();
    Files.createFile(dirDep.toPath().resolve("file"));
    File fileDep = temp.newFile();

    BuilderInputs inputs = builder() //
        .withDependencies(dirDep.getCanonicalPath(), fileDep.getCanonicalPath()) //
        .build(_PathLocationData.class);
    assertThat(inputs.getInputFiles()).contains(
        dirDep.getCanonicalFile().toPath().resolve("file"),
        fileDep.getCanonicalFile().toPath());
  }
  //
  //
  //

  static class _MetadataData {
    @Dependencies(scope = ResolutionScope.COMPILE)
    public List<IArtifactMetadata> dependencies;
  }
  
  @Test
  public void testDependencyMetadatasInput() throws Exception {
    String metadata1 = "a";
    String metadata2 = "b";
    
    File depFile1 = temp.newFile(metadata1).getCanonicalFile();
    File depFile2 = temp.newFile(metadata2).getCanonicalFile();
    
    @SuppressWarnings("unchecked")
    List<IArtifactMetadata> input = (List<IArtifactMetadata>) builder() //
        .withDependencies(depFile1.getCanonicalPath(), depFile2.getCanonicalPath()) //
        .build(_MetadataData.class, "dependencies").value();
    assertThat(input).hasSize(2);
    assertThat(input.get(0).getArtifactId()).isEqualTo("a");
    assertThat(input.get(1).getArtifactId()).isEqualTo("b");
  }

  @Test
  public void testDependencyMetadatasInput_directory() throws Exception {
    File dirDep = temp.newFolder().getCanonicalFile();
    Files.createFile(dirDep.toPath().resolve("file"));
    File fileDep = temp.newFile();

    BuilderInputs inputs = builder() //
        .withDependencies(dirDep.getCanonicalPath(), fileDep.getCanonicalPath()) //
        .build(_MetadataData.class);
    assertThat(inputs.getInputFiles()).isEmpty();
  }

  //
  //
  //
  
  static class _InvalidData {
    @Dependencies(scope = ResolutionScope.COMPILE)
    public List<Date> dependencies;
  }

  @Test(expected = InvalidModelException.class)
  public void testFailOnNonDependencyCollectionType() throws Exception {
    builder() //
        .withDependencies("dependency") //
        .build(_InvalidData.class, "dependencies");
  }
  
  //
  //
  //

  static class _MapData {
    @Dependencies(scope = ResolutionScope.COMPILE)
    public Map<IArtifactMetadata, File> dependencies;
  }

  @Test
  public void testDependencyMap() throws Exception {
    String metadata1 = "a:b:c";
    String metadata2 = "d:e:f";
    File fileDep = temp.newFile();
    File dirDep = temp.newFolder();

    new File(dirDep, "test.txt").createNewFile();

    @SuppressWarnings("unchecked")
    Map<IArtifactMetadata, File> input = (Map<IArtifactMetadata, File>) builder() //
        .withDependency(metadata1, fileDep) //
        .withDependency(metadata2, dirDep) //
        .build(_MapData.class, "dependencies").value();

    assertThat(input).hasSize(2);
    Iterator<Map.Entry<IArtifactMetadata, File>> it = input.entrySet().iterator();

    Map.Entry<IArtifactMetadata, File> entry1 = it.next();
    Map.Entry<IArtifactMetadata, File> entry2 = it.next();

    assertThat(entry1.getKey()).isEqualTo(new TestArtifactMetadata(metadata1));
    assertThat(entry2.getKey()).isEqualTo(new TestArtifactMetadata(metadata2));
    assertThat(entry1.getValue()).isEqualTo(fileDep);
    assertThat(entry2.getValue()).isEqualTo(dirDep);
  }

  //
  //
  //

  static class _MapPathData {
    @Dependencies(scope = ResolutionScope.COMPILE)
    public Map<IArtifactMetadata, Path> dependencies;
  }

  @Test
  public void testDependencyMapPath() throws Exception {
    String metadata1 = "a:b:c";
    String metadata2 = "d:e:f";
    File fileDep = temp.newFile();
    File dirDep = temp.newFolder();

    new File(dirDep, "test.txt").createNewFile();

    @SuppressWarnings("unchecked")
    Map<IArtifactMetadata, Path> input = (Map<IArtifactMetadata, Path>) builder() //
        .withDependency(metadata1, fileDep) //
        .withDependency(metadata2, dirDep) //
        .build(_MapPathData.class, "dependencies").value();

    assertThat(input).hasSize(2);
    Iterator<Map.Entry<IArtifactMetadata, Path>> it = input.entrySet().iterator();

    Map.Entry<IArtifactMetadata, Path> entry1 = it.next();
    Map.Entry<IArtifactMetadata, Path> entry2 = it.next();

    assertThat(entry1.getKey()).isEqualTo(new TestArtifactMetadata(metadata1));
    assertThat(entry2.getKey()).isEqualTo(new TestArtifactMetadata(metadata2));
    assertThat(entry1.getValue()).isEqualTo(fileDep.toPath());
    assertThat(entry2.getValue()).isEqualTo(dirDep.toPath());
  }
}
