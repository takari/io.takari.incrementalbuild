package whitelist.redundant;

import org.apache.maven.plugins.annotations.Mojo;

import io.takari.builder.Builder;
import io.takari.builder.internal.maven.AbstractIncrementalMojo;

// don't do this at home, kids.
// this is a **unit** test, normal builders do not extend AbstractIncrementalMojo

@Mojo(name = "builder")
public class ModernBuilderMojo extends AbstractIncrementalMojo {

  static class ModernBuilder {
    @Builder(name = "builder")
    public void execute() {}
  }

  protected ModernBuilderMojo() {
    super(ModernBuilder.class);
  }
}
