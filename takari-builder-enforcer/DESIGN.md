##What we are trying to achieve
* Guarantee correct build output in the presence of
  * concurrent multithreaded build that schedules projects based on their dependencies. each project is fully built on the same thread.
  * partial build, which replaces "binary" projects with their pre-built jar files without building the projects locally 
  * partial build with diff-based selection of “source” projects
 * Have better long-term maintainability of the codebase
  * hide “internal” project source layout from downstream consumers, changes to the layout do not affect consumers

For example, consider the following conventional multi-module directory structure

```
project/
 |-- pom.xml
 |-- moduleA/
 |    |-- pom.xml
 |    |-- target/
 |         |-- generated-resources/
 |         |-- classes/
 \-- moduleB
      |-- pom.xml
      |-- target/
           |-- classes/
```
… with the following unconventional generated resource reference
```
<project>
  <groupId>modularity</groupId>
  <artifactId>moduleB</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <build>
    <resources>
      <resource>
        <directory>../moduleA/target/generated-resources</directory>
      </resource>
    </resources>
  </build>
</project>
```
* Maven is not aware of implicit dependency between moduleB and moduleA
* Multithreaded build scheduler can build moduleB before moduleA/target/generated-resources is available
* Partial build does not guarantee moduleA/target/generated-resources is available



###What operations are allowed
* read anything under project basedir
* write under project build output directory
* read declared project dependency artifacts using standard Maven dependency resolution mechanisms
* load classes and resources from Maven plugin and MavenProject context classloaders
  * these will delegate to Maven core classloader and jvm application and bootstrap classloaders. the full classloader arrangement is rather dynamic: maven 3.3.x cloassloading.
* execute java through sandboxing exec plugin, which propagates enforced rules to the forked jvm

###What operations are kinda okay (can break if multiple rules "touch" the same file)
* read non-generated, i.e. version controlled, resources from dependency projects. 
  * only guaranteed to work with full checkout. will have to be blocked if/when we decide to do partial checkout. 
  * telling what files are under version control is not trivial in practice. "project source or resource resource folder not under build output directory" is probably the best approximation.
  * use of MavenProject model to locate other project resources is generally okay; use of hardcoded paths will likely cause maintainability problems in the long run, no way to distinguish the two cases from modularity enforcer
* write under this project basedir but outside build output directory. as long as these generated resources cannot be confused with resources under version control, which is not trivial (see above).
* read files under maven local repository and any file:// repository enabled for the project. maven may need to access these files for any number of reasons not directly related to the project build. no practical way to block these without changes to maven core
* write files under Maven local repository. Maven uses local repository to cache artifacts from remote repositories and as a way to share artifacts among builds running on the same system (core app and ui-tier, for example). no practical way to block this
* write outside any project basedir. does not really matter if the written files are not accessed by other projects
* read dependency project build output directory if the dependency project is in source mode.

###What operations will almost certainly cause problems and why
* read from non-dependency project, several cases, all bad
  * read build output directory. projects without direct dependency can be built in arbitrary order, requested resources may not exist or be stale
  * read version controlled sources, diff-based source project selected (a.k.a. “partial build in precheckin”) will not select all affected projects, build results may be incorrect
  * note that Maven will read pom.xml files of all projects to resolve inter-project dependencies, partial build will need to special-case pom.xml changes somehow (which it may already do)
* read from dependency project build output directory if the dependency project is in binary mode during partial build, the output directory is empty or contains stale contents
* write under other project basedir, several cases, all bad
  * dependency project. files written will not be part of this build but be part of the next build of the dependency project. dependency project build results are wrong either during the first or the consequent build 
  * downstream project. the files will not be generated if this project is in binary mode during partial build. files written under build output directory will be removed during clean build.
  * non-dependency projects. build order is not guaranteed, build results are unpredictable and wrong.
* read outside of any project basedir. no way to guarantee the files are present or the contents as expected, the build results are generally unpredictable
* fork random processes. really don’t know what they do and can’t guarantee anything.
* anything I have not thought about, let’s assume it will cause build the failed in the most hard to troubleshoot but harmful manner

##Forked JVM Instances from Maven

Execution of build steps in “forked” jvm instances (e.g., Google Closure compiler invocation during core/canvas build), will be supported by a custom Maven plugin that will install the same security manager in the forked jvm. Execution of external processes using any other mechanism will be rejected by the security manager both in Maven build and forked jvms.

##Known violations (as of 204 CLCO on 2016-07-30)
* Some projects execute p4.
* Some generated resources are checked into version control system.
* Java annotation processing during “pre”, i.e. without compilation, requires access to dependency projects sources and generated sources directories. This is “okay” because the compiler knows how to deal. 
* eclipse/idea projects are generated outside of target.
* eclipse/idea generator ignores project boundaries.
* plsql generators ignore project boundaries.


## Outstanding issues and future improvements

* Direct filesystem access to files under Maven local repository. 
  * Generally, the build should not make any assumptions about local repository layout or contents. For example, referenced files may only be cached in local repository as side-effect of another project build and will be missing if the other project is in binary mode, scheduled to build later or removes the dependency on the referenced files. Similarly, referenced files can be mvn-installed to local repository and will be missing of the build does not run install phase.
  * As of Maven 3.3.3, project dependencies are resolved lazily, as part of execution of individual plugin goals. Furthermore, plugins are resolved and loaded as needed during the build. This makes it impossible to distinguish between legitimate use of Maven <dependency> mechanism and direct access to files under Maven local repository.
  * Need to change Maven to eagerly resolve project <dependencies> and <plugins> in order to restrict direct access to Maven local repository.
* Direct filesystem access to files under core/ext and other filesystem Maven repositories.
  * Similarly to direct access to Maven local repository, blocking direct access to other filesystem repositories will require changes to Maven core to eagerly resolve project dependencies.
* Direct access to files resolved as Maven <dependencies> or <plugins>
  * This will become a problem when declared dependencies changes, either in the project itself or through inherited project configuration (aka “dependency and plugin management”). Impossible to detect because such access is indistinguishable from legitimate use at SecurityManager level.
* Ad-hoc file sharing using shared filesystem locations like /tmp
  * One project build creates files under shared filesystem location, other projects builds use the files.
  * Possible to detect but will require relatively long and hard to maintain whitelist of allowed external locations.
* Direct access to external filesystem locations like /etc/hosts
  * Generally, the build should assume as little about environment as possible. Direct references to external filesystem locations make it harder to the build different environments. Not strictly related to partial build and smart multithreaded builder, but the same security manager can be used to detect and block any unwanted filesystem access.
* Limiting module output to target/classes
  * If a module build outputs files into say ‘webresource/gen’ or ‘target/htdocs’ and uses them directly from those location at runtime, then the module will be broken in partial build mode. Right now we defer any such ‘provisioning’ actions to the core-app maven project. But that’s just by convention and not enforced. This problem actually happened when someone modified jslibrary and forgot to do the copy/extraction in core-app.
* Multithreaded code
  * Java SecurityManager implementation relies on ThreadLocal to determine currently build project and corresponding allowed/denied filesystem locations. This does not work if Maven core or maven plugins used by the project build execute work on multiple threads. Known offenders
  * Aether runs dependency download on multiple threads by default. This is not an issue because this cannot affect project build order and/or partial build invariants.
