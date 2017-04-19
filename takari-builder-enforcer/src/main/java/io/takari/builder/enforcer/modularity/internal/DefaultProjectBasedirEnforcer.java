package io.takari.builder.enforcer.modularity.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.takari.builder.enforcer.ComposableSecurityManagerPolicy;
import io.takari.builder.enforcer.internal.EnforcerConfig;
import io.takari.builder.enforcer.internal.EnforcerViolation;
import io.takari.builder.enforcer.internal.EnforcerViolationType;
import io.takari.builder.enforcer.modularity.ModularityEnforcementPolicy;
import io.takari.builder.enforcer.modularity.ProjectContext;
import io.takari.builder.enforcer.modularity.maven.ProjectBasedirEnforcer;
import io.takari.builder.internal.pathmatcher.PathMatcher;
import io.takari.builder.internal.pathmatcher.PathNormalizer;

@Named
@Singleton
public class DefaultProjectBasedirEnforcer implements ProjectBasedirEnforcer {
  private final Logger log = LoggerFactory.getLogger("SPRINKLING");

  private static final Object KEY_CONTEXT = DefaultProjectBasedirEnforcer.class;

  private void registerContextPolicy(ProjectContext context) {
    ComposableSecurityManagerPolicy.registerContextPolicy(KEY_CONTEXT,
        new ModularityEnforcementPolicy(context, (c, v) -> handleViolation(c, v)));
  }

  private ModularityEnforcementPolicy unregisterContextPolicy() {
    ModularityEnforcementPolicy policy = (ModularityEnforcementPolicy)ComposableSecurityManagerPolicy
        .unregisterContextPolicy(KEY_CONTEXT);
    policy.close();
    return policy;
  }

  ModularityEnforcementPolicy getContextPolicy() {
    return (ModularityEnforcementPolicy)ComposableSecurityManagerPolicy.getContextPolicy(KEY_CONTEXT);
  }

  private PathNormalizer normalizer;

  private PathMatcher readMatcher;

  private PathMatcher writeMatcher;

  private final Collection<ProjectsProvider> projectsProviders;

  private final DefaultPlexusContainer plexus;

  @Inject
  public DefaultProjectBasedirEnforcer(Collection<ProjectsProvider> projectsProviders, DefaultPlexusContainer plexus) {
    this.projectsProviders = projectsProviders; // live injected collection, do not copy
    this.plexus = plexus;
  }

  @Override
  public boolean isEnabledForProject(EnforcerConfig config, String artifactId) {
    return config != null && config.enforce() && !config.exclude(artifactId);
  }

  protected boolean isAncestorOf(MavenProject project, MavenProject possibleAncestor) {
    MavenProject current = project;
    while (current != null) {
      current = current.getParent();
      if (possibleAncestor.equals(current)) { return true; }
    }
    return false;
  }

  public void setupProjectContext(MavenSession session, MavenProject project, SessionConfig sessionConfig,
      EnforcerConfig enforcerConfig) {
    if (!isEnabledForProject(enforcerConfig, project.getArtifactId())) { return; }

    boolean enforceProject = isModularityEnforcementEnabled(project);

    if (enforceProject) {

      PathMatcher.Builder readMatcherBuilder = PathMatcher.builder(this.normalizer).addMatcher(this.readMatcher);
      PathMatcher.Builder writeMatcherBuilder = PathMatcher.builder(this.normalizer).addMatcher(this.writeMatcher);

      // ALLOW read everything from project basedir
      readMatcherBuilder.includePrefix(project.getBasedir().getAbsolutePath());

      Set<MavenProject> upstreamProjects = getUpstreamProjects(session, project);

      // ALLOW read reactor dependencies
      for (MavenProject other : upstreamProjects) {
        if ("pom".equals(other.getPackaging())) {
          // see org.apache.maven.ReactorReader.find(MavenProject, Artifact)
          readMatcherBuilder.includePath(other.getFile().getAbsolutePath());
        } else {
          // dependency resolver needs these *files* to construct other project models
          // see org.apache.maven.model.path.DefaultModelPathTranslator.alignToBaseDirectory
          Build otherBuild = other.getBuild();
          if (!isAncestorOf(project, other)) {
            for (String root : other.getTestCompileSourceRoots()) {
              readMatcherBuilder.includePrefix(root);
            }

            for (String root : other.getCompileSourceRoots()) {
              readMatcherBuilder.includePrefix(root);
            }
            // readIncludesBuilder.addFile(otherBuild.getSourceDirectory());
            // readIncludesBuilder.addFile(otherBuild.getTestSourceDirectory());
            readMatcherBuilder.includePath(otherBuild.getScriptSourceDirectory());
            for (Resource otherResource : otherBuild.getResources()) {
              readMatcherBuilder.includePath(otherResource.getDirectory());
            }
            for (Resource otherResource : otherBuild.getTestResources()) {
              readMatcherBuilder.includePath(otherResource.getDirectory());
            }
            readMatcherBuilder.includePath(otherBuild.getOutputDirectory());
            readMatcherBuilder.includePath(otherBuild.getTestOutputDirectory());
            readMatcherBuilder.includePrefix(otherBuild.getDirectory());
            readMatcherBuilder.includePrefix(other.getBuild().getOutputDirectory());
            readMatcherBuilder.includePrefix(other.getBuild().getTestOutputDirectory());
          }

          // inherit parents deps.
          for (Artifact artifact : other.getAttachedArtifacts()) {
            readMatcherBuilder.includePrefix(artifact.getFile().getAbsolutePath());
          }
        }
      }

      // ALLOW project dependency and plugin repositories (required by Maven dependency resolver)
      for (ArtifactRepository repo : project.getRemoteArtifactRepositories()) {
        addRepositoryBasedir(readMatcherBuilder, repo);
      }
      for (ArtifactRepository repo : project.getPluginArtifactRepositories()) {
        addRepositoryBasedir(readMatcherBuilder, repo);
      }

      // ALLOW write to project output directory
      writeMatcherBuilder.includePrefix(project.getBuild().getDirectory());

      // ALLOW write to local repository (required by Maven dependency resolver)
      writeMatcherBuilder.includePrefix(session.getLocalRepository().getBasedir());

      if (sessionConfig.isWriteByDefault()) { // ALLOW write within project tree - nested projects are excluded
                                              // separately in {@link #setupMavenSession}
        writeMatcherBuilder.includePrefix(project.getBasedir().getAbsolutePath());
      }
      // configured policy exceptions
      addExceptions(readMatcherBuilder, session, project, enforcerConfig.getReadExceptions(project.getArtifactId()),
          sessionConfig.isAllowBreakingRules(), upstreamProjects);
      addExceptions(writeMatcherBuilder, session, project, enforcerConfig.getWriteExceptions(project.getArtifactId()),
          sessionConfig.isAllowBreakingRules(), upstreamProjects);

      PathMatcher readMatcher = readMatcherBuilder.build();
      PathMatcher writeMatcher = writeMatcherBuilder.build();
      Set<String> execIncludes = new HashSet<>();
      addExecExceptions(execIncludes, enforcerConfig.getExecExceptions(project.getArtifactId()));
      ProjectContext context = new ProjectContext(normalizer, project.getArtifactId(), readMatcher, writeMatcher,
          execIncludes);
      registerContextPolicy(context);
    }
  }

  /**
   * Returns {@code true} if build enforcement is configured for the project (usually via inheritance), otherwise
   * returns {@code false}. This is necessary to support multimodule project where enforcement is enabled for some but
   * not all modules.
   */
  // TODO this check is not specific to modularity enforcement, move to a more generic class
  protected Boolean isModularityEnforcementEnabled(MavenProject project) {
    ClassRealm realm = project.getClassRealm();
    if (realm == null) {
      // enforcer can be enabled in maven runtime
      realm = plexus.getContainerRealm();
    }
    // attempt to load any enforcer component from the project class realm
    // if the component is not visible or does not match, the extension isn't configured
    try {
      Class<?> clazz = io.takari.builder.enforcer.Policy.class;
      return realm.loadClass(clazz.getCanonicalName()) == clazz;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  protected Set<MavenProject> getUpstreamProjects(MavenSession session, MavenProject project) {
    ProjectDependencyGraph graph = session.getProjectDependencyGraph();
    Set<MavenProject> projects = new HashSet<>(graph.getUpstreamProjects(project, true));
    for (ProjectsProvider provider : projectsProviders) {
      projects.addAll(provider.getUpstreamProjects(session, project, true));
    }
    return projects;
  }

  protected Set<MavenProject> getAllProjects(MavenSession session, MavenProject project) {
    Set<MavenProject> projects = new HashSet<>();
    projects.addAll(session.getAllProjects());
    for (ProjectsProvider provider : projectsProviders) {
      projects.addAll(provider.getAllProjects(session));
    }
    return projects;
  }

  public void finishProjectContext(String basedir, MavenProject mavenProject, SessionConfig config) {
    if (getContextPolicy() == null) { return; }

    ProjectContext project = unregisterContextPolicy().getProjectContext();
    if (project.getViolations().isEmpty()) { return; }

    Set<String> violatedRules = new HashSet<>();
    for (EnforcerViolation violation : project.getViolations()) {
      String type = violation.getType();
      if (violation.getViolationType() == EnforcerViolationType.EXECUTE) {
        violatedRules.add(type + " " + violation.getFile());
      } else {
        violatedRules.add(type + " " + project.matchingRule(type.charAt(0), violation.getFile()));
      }
    }

    StringBuilder msg = new StringBuilder();
    msg.append("\nUnexpected filesystem access for project: ").append(project.getId());
    msg.append("\nViolated Rules Are:");
    for (String violation : violatedRules) {
      msg.append("\n   " + violation);
    }
    msg.append("\nFor current exceptions please see: " + basedir + "/.mvn/basedir_enforcer.config");
    msg.append(
        "\n    Rule1: Projects in a reactor build should only read from the published resources of their dependencies.");
    msg.append(
        "\n           Maven lifecycle may require reading a dependencies classes, rather than a jar, or sources rather than classes.");
    msg.append("\n    Rule2: Projects should only write to ./target directory");
    msg.append("\n");
    String butc_chatter = "https://gus.my.salesforce.com/_ui/core/chatter/groups/GroupProfilePage?g=0F9B000000000lg";
    msg.append(String.format("\nSee %s for more information", butc_chatter));
    if (config.isLogOnly()) {
      log.error(msg.toString());
    } else {
      throw new BasedirViolationException(msg.toString());
    }
  }

  private static final String START = "${";
  private static final String STOP = "}";
  private static final String UPSTREAM = "dependency.";
  private static final String PROJECT = "project.";
  private static final String ALL_UPSTREAM = "?ALL";
  private static final String ARTIFACT_ID = "artifactId";

  private String interpolate(String input, MavenProject project, MavenProject upstream) {
    return input
        .replace(START + UPSTREAM + ARTIFACT_ID + STOP, upstream == null ? "NO_UPSTREAM" : upstream.getArtifactId())
        .replace(START + PROJECT + ARTIFACT_ID + STOP, project.getArtifactId());
  }

  private static final Pattern pattern = Pattern.compile("^\\$\\{dependency\\.(\\??[a-zA-Z0-9\\-]*)\\}");

  private void processExceptions(String relpath, MavenProject project, Set<MavenProject> upstream,
      PathMatcher.Builder builder, Set<MavenProject> allProjects, File multiModuleProjectDirectory,
      boolean allowBreakingRules) {
    Matcher matcher = pattern.matcher(relpath);
    if (matcher.find()) {
      String matchDependency = matcher.group(1);
      String rest = relpath.substring(matcher.end(0));
      Set<MavenProject> toProcess;
      boolean allowToIgnore = false;
      if (ALL_UPSTREAM.equals(matchDependency)) {
        toProcess = upstream;
        allowToIgnore = true;
      } else {
        toProcess = upstream.stream().filter(p -> matchDependency.equals(p.getArtifactId()))
            .collect(Collectors.toSet());
      }
      if (toProcess.isEmpty()) {
        log.warn("No upstream project match " + matcher.group(0) + " for project " + project.getArtifactId());
      }
      // check here......
      for (MavenProject other : toProcess) {
        addException(builder, other.getBasedir(), interpolate(rest, project, other), allowBreakingRules, allowToIgnore);
      }
    } else {
      File basedir = relpath.startsWith("/") ? multiModuleProjectDirectory : project.getBasedir();
      addException(builder, basedir, interpolate(relpath, project, null), allowBreakingRules, false); // this could be a
                                                                                                      // read exclusion
                                                                                                      // which would be
                                                                                                      // pointless
    }
  }

  protected void addExceptions(PathMatcher.Builder builder, MavenSession session, MavenProject project,
      Collection<String> exceptions, boolean allowBreakingRules, Set<MavenProject> upstreamProjects) {
    File multiModuleProjectDirectory = session.getRequest().getMultiModuleProjectDirectory();
    for (String relpath : exceptions) {
      processExceptions(relpath, project, upstreamProjects, builder, getAllProjects(session),
          multiModuleProjectDirectory, allowBreakingRules);
    }
  }

  protected void addExecExceptions(Set<String> updated, Collection<String> exceptions) {
    for (String relpath : exceptions) {
      updated.add(relpath);
    }
  }

  /**
   * @param builder
   *          aggregator of allowed rules
   * @param basedir
   *          part of file path
   * @param relpath
   *          rest of file path (may include bs like /../ or /./
   * @param allowBreakingRules
   *          if a rule can break strict modularity (aka the maven build is no longer a Directed Acyclic Graph)
   * @param allowToIgnore
   *          whether or not a rule should be ignored if it is a breaking rule (see allowBreakingRules)
   */
  protected void addException(PathMatcher.Builder builder, File basedir, String relpath, boolean allowBreakingRules,
      boolean allowToIgnore) {
    final String base = normalizer.normalize(basedir.toPath());
    final String path = normalizer
        .normalize(basedir.toPath().resolve(relpath.startsWith("/") ? relpath.substring(1) : relpath));
    String baseOwner = readMatcher.getMatchingRule(base);
    String actualOwner = readMatcher.getMatchingRule(path);
    boolean invalidRule = !baseOwner.equals(actualOwner);

    String from = baseOwner != null ? "another maven project " + actualOwner + " in subfolder " + baseOwner
        : "a non maven project path " + path;
    if (allowToIgnore && invalidRule) {
      if (baseOwner != null) {
        log.info("In ./.mvn/basedir-enforcer.txt you have a rule " + relpath + " on target project at " + basedir
            + " that tries to access a file from " + from + " the rule will be ignored");
      }
      return;
    }
    if (invalidRule && allowBreakingRules) {
      log.warn("In ./.mvn/basedir-enforcer.txt you have a rule " + relpath + " on target project at " + basedir
          + " that tries to access a file from " + from
          + " the rule will be ALLOWED, but your build is invalid... especially a partial build");
    }
    if (invalidRule && !allowBreakingRules) {
      log.warn("In ./.mvn/basedir-enforcer.txt you have a rule " + relpath + " on target project at " + basedir
          + " that tries to access a file from " + from + " the rule will be IGNORED");
    }
    if (!invalidRule || allowBreakingRules) {
      if (relpath.endsWith("/")) {
        builder.includePrefix(path);
      } else {
        builder.includePath(path);
      }
    }
  }

  public void setupMavenSession(MavenSession session, SessionConfig sessionConfig) {
    this.normalizer = new PathNormalizer(session.getRequest().getMultiModuleProjectDirectory().toPath());

    PathMatcher.Builder readMatcherBuilder = PathMatcher.builder(normalizer).excludeRoot();
    PathMatcher.Builder writeMatcherBuilder = PathMatcher.builder(normalizer).excludeRoot();

    for (MavenProject project : getAllProjects(session)) {
      readMatcherBuilder.includePath(project.getFile().getAbsolutePath()); // used by maven resolver
      readMatcherBuilder.excludePrefix(project.getBasedir().getAbsolutePath());
      writeMatcherBuilder.excludePrefix(project.getBasedir().getAbsolutePath());
    }
    // Root folder will be examined when reading contents... exec commands and others will need this.
    readMatcherBuilder.includePath(session.getLocalRepository().getBasedir());
    readMatcherBuilder.includePrefix(session.getLocalRepository().getBasedir());
    // addException(includesBuilder, new File(session.getLocalRepository().getBasedir()), "./");

    // since permission is given when the read line is greater than OR EQUAL TO the exclude line, this disables read for
    // all locations by default
    if (sessionConfig.isReadByDefault()) {
      readMatcherBuilder.includeRoot();
    }
    // by default can't write everywhere.
    if (sessionConfig.isWriteByDefault()) {
      writeMatcherBuilder.includeRoot();
    }

    Arrays.asList("java.endorsed.dirs", "java.ext.dirs", "java.home", "java.io.tmpdir", "java.class.path",
        "java.library.path", // standard jvm props
        "sun.boot.library.path", // classpath
        "maven.home", // maven installation, maven core jars and default configuration
        "maven.ext.class.path" // maven extensions, used to inject hudson eventspy and such
    ).stream().map(prop -> System.getProperty(prop)).filter(propVal -> propVal != null && !"".equals(propVal)) // ignore
                                                                                                               // empty
                                                                                                               // or
                                                                                                               // unset
                                                                                                               // properties
        .flatMap(propVal -> Arrays.asList(propVal.split(File.pathSeparator)).stream())
        .filter(path -> path != null && !".".equals(path) && !"".equals(path)) // don't include empty place or current
                                                                               // location
        .map(path -> path.endsWith(File.separator + "jre") ? path.substring(0, path.length() - 3) : path) // java.home
                                                                                                          // need to ../
                                                                                                          // from the
                                                                                                          // jre dir to
                                                                                                          // get the
                                                                                                          // other jars.
        .forEach(path -> readMatcherBuilder.includePrefix(path));

    // ALLOW maven core libraries, used to inject m2e workspace dependency resolver
    // this allows all class realms, which should be maven core at this point
    plexus.getClassWorld().getRealms().stream() //
        .flatMap(r -> Arrays.asList(r.getURLs()).stream()) //
        .forEach(url -> {
          try {
            includeDirectory(readMatcherBuilder, url.toURI());
          } catch (URISyntaxException e) {
            // too bad
          }
        });

    // ALLOW check of multimodule project directory exists
    // required by org.apache.maven.plugin.ide.intellij.IntellijPlugin
    readMatcherBuilder.includePath(session.getRequest().getMultiModuleProjectDirectory().getAbsolutePath());

    // TODO: just adding this here for now to allow this read, but will do this in a cleaner way
    readMatcherBuilder
        .includePrefix(new File(session.getRequest().getMultiModuleProjectDirectory(), ".mvn").getAbsolutePath());

    // ALLOW read/write temp directory
    readMatcherBuilder.includePrefix(System.getProperty("java.io.tmpdir"));
    writeMatcherBuilder.includePrefix(System.getProperty("java.io.tmpdir"));

    // ALLOW Access to random number generator...
    readMatcherBuilder.includePath("/dev/random").includePath("/dev/urandom");

    readMatcher = readMatcherBuilder.build();
    writeMatcher = writeMatcherBuilder.build();
  }

  /**
   * TODO: do once and cache
   * 
   * @param session
   * @return
   */
  protected Set<MavenProject> getAllProjects(MavenSession session) {
    Set<MavenProject> projects = new HashSet<>(session.getAllProjects());
    for (ProjectsProvider provider : projectsProviders) {
      projects.addAll(provider.getAllProjects(session));
    }
    return projects;
  }

  @Override
  public void replayLog(File logfile) throws IOException {
    ModularityEnforcementPolicy policy = getContextPolicy();
    if (policy != null) {
      ProjectContext project = policy.getProjectContext();
      for (EnforcerViolation violation : EnforcerViolation.readFrom(logfile.toPath())) {
        handleViolation(project, violation);
      }
    }
  }

  protected void handleViolation(ProjectContext context, EnforcerViolation violation) {
    if (context != null) {
      if (context.addViolation(violation)) {
        String type = violation.getType();
        String file = violation.getFile();
        String rule = context.matchingRule(type.charAt(0), file);
        String message = type + " " + file + " " + rule;
        StringBuilder details = new StringBuilder(message);
        violation.getStackTrace().forEach(s -> details.append("\n   | ").append(s));
        log.error(details.toString());
      }
    }
  }

  @Override
  public void writeConfiguration(File file) throws IOException {
    ModularityEnforcementPolicy policy = getContextPolicy();
    if (policy != null) {
      try (FileOutputStream out = new FileOutputStream(file)) {
        policy.getProjectContext().store(out);
      }
    }
  }

  private static void addRepositoryBasedir(PathMatcher.Builder builder, ArtifactRepository repo) {
    try {
      includeDirectory(builder, new URI(repo.getUrl()));
    } catch (URISyntaxException ignored) {
      // too bad
    }
  }

  private static void includeDirectory(PathMatcher.Builder builder, URI url) {
    if ("ext".equals(url.getScheme()) || "file".equals(url.getScheme())) {
      builder.includePrefix(url.getPath());
    }
  }

  @Override
  public void enterExecPrivileged() {
    ModularityEnforcementPolicy policy = getContextPolicy();
    if (policy != null) {
      policy.enterExecPrivileged();
    }
  }

  @Override
  public void leaveExecPrivileged() {
    ModularityEnforcementPolicy policy = getContextPolicy();
    if (policy != null) {
      policy.leaveExecPrivileged();
    }
  }

}
