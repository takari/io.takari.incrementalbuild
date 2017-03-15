# Takari-Builder Incremental Build Framework User Documentation

## Table of Contents
 * [Motivation](#motivation)
 * [Terminology and Basic Concepts](#terminology-basics)
 * [Incremental BuilAPI Overview](#api-overview)
 * [Threading Model](#thread-model)
 * [Files vs. Classpath Resources](#files-v-classpath)
 * [Recommended Builder Project Structure](#recommended-structure)
 * [Detailed @Annotation Descriptions](#detailed-annotations-description)
 * [Builder Testing](#builder-testing)
 * [Migrating Existing Maven Plugins](#Maven-plugin-migration)

<a id="motivation"></a>
## Motivation

The goal of this project is to provide an easy-to-use framework to implement deterministic (or "repeatable") and efficient builds for Java-based projects. This guide focuses on the use-case of migrating existing build plugins to the Takari-Builder Incremental Build Framework.

For build tools (e.g., Maven plugin) developers the framework provides:

- non-intrusive annotation-based API
- a runtime to automate common implementation tasks
  - tracks all build inputs, only executes the build when inputs change
  - tracks all build outputs, cleans up obsolete outputs
  - controls access to system resources to prevent common implementation mistakes
- a unit test harness
- Eclipse IDE integration

<a id="terminology-basics"></a>
## Terminology and Basic Concepts

***Builders*** are user-provided components that implement specific build actions, like Java compilation or Javascript minification.

A builder execution can produce one or more of the following ***builder outputs*** types:

* generated/output files on filesystem
* build messages, including overall execution sucess/failure flag
* project model mutations, which are limited to additional project sources and/or resources directories

Builder outputs are assumed to be fully defined by the following ***builder inputs*** types:

* builder classpath
* builder configuration parameter values
* builder input file contents, including any project dependencies used by the builder
* system properties used by the builder

In other words, builders are assumed to be deterministic in regards to their inputs. Given the same (as in "byte-identical") inputs set, builders are assumed to produce the same outputs. In cases of the builders that modified their own inputs, builders are also assumed to be idempotent, i.e. running the builder once for a given set of inputs is assumed to produce the same outputs as running the builder two or more times for the same set of inputs. 

Under this assumption it is possible to skip builder execution if the builder inputs are the same compared to the prior builder execution. At a high level, the coarse-grained incremental build implementation will do the following:

* Calculate builder input parameter values
* If builder inputs did not change since the previous builder execution:
  * re-apply project model mutations
  * replay builder messages
  * set builder execution success/failure flag
* If any of the builder inputs changed since the previous builder execution:
  * delete the builder output files created during the previous build
  * create a new builder instance and inject input parameter values into the instance
  * execute the builder
  * persist builder inputs, outputs and messages for use in future builders
  * set builder execution success/failure flag

In order to ensure that builders do what they say they are going to, they run within a constrained execution environment where:

* they can only read from declared input locations
* they can only read from declared system properties
* they can only write to declared output locations

If any builder attempts to access undeclared resources, the build will fail, as it can no longer be considered idempotent.

<a id="api-overview"></a>
## Incremental Build API Overview

As mentioned above, specific build actions (like Java compilation and Javascript minification) are performed by user-provided components called builders. Here is an example of a very simple builder class:

```Java
public class MyBuilder {
  @Parameter
  private String message;

  @OutputDirectory(defaultValue="${project.build.directory}")
  private File target;

  @Builder(name = "print")
  public void print() throws IOException {
    Charset charset = Charset.forName("UTF-8");
    Path path = new File(target, "message.txt").toPath();
    try (Writer w = Files.newBufferedWriter(path, charset)) {
      w.write(message);
    }
  }
}
```

This class defines a builder that has the name "print", two input parameters, "message" and "target", and writes the message to a file in the output directory. The builder can be used in a Maven pom.xml file:

```xml
  <plugin>
    <groupId>...</groupId>
    <artifactId>...</artifactId>
    <version>...</version>
    <executions>
      <execution>
        <id>print</id>
        <phase>compile</phase>
        <goals><goal>print</goal></goals>
        <configuration>
          <message>bulder message</message>
        </configuration>
      </execution>
    </executions>
  </plugin>
```

### Builder Implementation Requirements
Overall, the framework imposes very few requirements on the builder implementation. Those requirements are:

- The Builder class must be a non-abstract public top-level class or static member type
- The Builder method must be annotated with the `@Builder` annotation
- All builder input parameters must be annotated with corresponding @Annotations
- The Builder implementation must be deterministic and idempotent, although this is difficult to guarantee in practice

### @Builder(name = "") Annotation

`@Builder` annotates the method that implements the action the builder performs during the build.

For each `@Builder` annotated method, a Maven mojo will be generated. The mojo wraps the method, executing it as part of the Maven lifecycle if any of the declared inputs have changed or if the builder is running for the first time, or skipping the execution and replaying the messages from the previous execution if inputs have not changed.

The `@Builder` annotation has a single required `name` attribute, which declares the builder name. The name corresponds to the Maven plugin goal name and ***must be unique*** among all builders in the same builder project. In addition to `name`, the `@Builder` annotation contains two other optional attributes: `defaultPhase` (defaults to `LifecyclePhase.NONE`), and `requiresProject` (defaults to True). These attributes will map to the similarly named attributes in Maven's `@Mojo` annotation in the final generated Mojo.

`@Builder` methods must be public, non-abstract, non-static, and have no parameters. They can throw any checked or unchecked exceptions. The Builder class can have multiple `@Builder` methods, but the methods will share input and output parameters.

### Messages

Messages about problems with builder input files or configuration can be communicated to the user with the `Messages` API. 

The `Messages` API, as opposed to debug logging, is used to represent a problem with user-provided builder inputs and provides messages meant to be seen by the user. Debug logging, on the other hand, provides insight into what the Builder is doing and is mostly useful for Builder developers.

Messages recorded using the `Messages` instance can later be replayed if the builder execution has been skipped (no inputs changed). `Messages` has three methods, `info`, `warn`, and `error`, all with the same method signature, as shown below.

```Java
Messages messages = BuilderContext.getMessages();

Logger log = LoggerFactory.getLogger(MyBuilderClass.class);

@InputFile("${project.basedir}/src/main/resources/myFileToParse.txt")
File inputFile;

@Builder(name="file-parser")
public void execute() {
    log(String.format("Parsing file: %s", inputFile.getAbsolutePath()));
    try {
        Object parsed = parseIt(inputFile);
    } catch (CustomParsingException e) {
        messages.error(inputFile, e.getLineNo(), e.getColNo(), e.getMessage(), e);
    }
}

private Object parseIt(file fileToParse) throws CustomParsingException {
    //...
}     
```

### Input and Ouput Parameter Annotations
The other annotations in the Takari-Builder framework are used for declaring inputs and outputs. These all follow a similar pattern for declaring where the value comes from:

- if `value` is used, this will be a read-only property with the value as defined
- if `defaultValue` is used, this will be a configurable property which will use either a value provided through the pom file, or the default value as specified
- if neither of these is specified, the value will come from the configured value in the POM file

If the parameter is not required, and no value/defaultValue is specified or configured in the pom.xml, the value will be `Null`. (This is not supported with Primitive types. Parameters with Primitive types should always be required.)

#### Supported Annotations

- [`@Dependencies`](#dependenciesscope-transitive) - collection of project dependencies
- [`@DependencyResources`](#dependencyresourcesscope) - A collection of URLs to resources within a project's dependencies
- [`@ArtifactResources`](#artifactresourcesscope-resourcesrequired-includes-excludes) - A collection of URLs to resources within a set of configured artifacts
- [`@GeneratedResourcesDirectory`](#generatedresourcesdirectoryvalue-type-includes-excludes) - A directory where generated resource files will be written
- [`@GeneratedSourcesDirectory`](#generatedsourcesdirectoryvalue) - A directory where generated source files will be written
- [`@InputDirectory`](#inputdirectoryvalue-includes-excludes) - A directory where input files are located
- [`@InputDirectoryFiles`](#inputdirectoryfiles-and-dependencyresources) - A collection of Files within a specified input directory
- [`@InputFile`](#inputfile) - A specific input file
- [`@OutputDirectory`](#outputdirectory) - A directory where output files will be written
- [`@OutputFile`](#outputfile) - A specific output file
- [`@Parameter`](#parametervalue) - Any other parameter used as input for the builder

### Access to Project Properties
In order to ensure a deterministic build, access to Maven's project properties has been limited to a small set of expressions that will be resolved when the value is read. These can be specified withing the annotation declaration, as well as within the configuration in the pom.xml:

```Java
@Parameter(defaultValue = "${project.basedir}")
File basedir;
```

```xml
<basedir>${project.basedir}/otherdir</basedir>
```

#### Project Properties Supported as Expressions

- `project.groupId` - the project's groupId
- `project.artifactId` - the project's artifactId
- `project.version` - the project's version
- `project.basedir` - the base directory of the project
- `project.build.directory` - the project's default output directory (usually `target`)
- `project.build.outputDirectory` - the project's output classes directory (usually `target/classes`)
- `project.build.testOutputDirectory` - the project's output test classes directory (usually `target/test-classes`)
- Any User or System defined properties

## Creating a Builder that Operates as a Pure Mathematical Function


```diff
- WARNING: Read this carefully. You must be at least this tall to ride.
```

Your code should be deterministic and idempotent, and your builder must opperate as a pure mathematical function. For the exact same defined input, you should produce the exact same byte for byte output. How do you achieve this behavior in a very stateful system like the JVM, with an object oriented programming language like Java? 

With a lot of help.

### Working With the Builder API

The Builder API offers carrots and sticks. It is not perfect, and there are holes. We will try to fill those holes. As we do that, we may break your code, so pay close attention.

- Do not rely on any static member variables not set by your builder during its executions. We reserve the right to wipe every single static member variable in the JVM.

- We will break any attempt to read a file (and soon system properties) that you do not declare as inputs. You should never write system properties. We will likely break attempts by your build's code to set system properties. Please note that the JVM's classes may read (and initialize) system properties through calls in your code. These environment variables and system property access are part of the JVM implementation. 

- We assume operations performed by the JVM (and Java core code libraries) to be safe, but please keep in mind not all data types in the JVM are deterministic. Unordered datatype will cause you problems (HashMaps, Set) unless you sort them. Using current Dates or Times, or environment specific data like timezone. Even the hashcode of a Java object (based on memory location for Java.lang.Objects). Dont even think about Math.random() or Java.lang.Random. 

- Hopefully we'll put some PMD rules or other enforcement together for you, as well as the security manager to enforce SystemProperties.

<a id="thread-model"></a>
## Threading Model

```diff
- WARNING: If you will use threads or parallel streams or executors read this carefully.
```

Runnables, Callables, and work done from within a Java 8 stream sometimes runs in separate threads from the ones that created the work. We attach your permissions to the thread your Builder is executing in.

You declare the files, classpath and system properties your Builder has the right to access through annotations. Work you pass to these threadpools will not be able to access the files or system properties your build has the right to access due to its declarations. 

We do give you an out. You may pass your Builder's permissions along with any callable or runnable. You may use a PolicyContextPreserver to wrap any work you pass to another thread with your Builder's permissions.

Please see this test of the PolicyContextPreserver to understand its usage. We must instantiate the context in the thread that has permission to do work [ThreadContextManagerTest.java Line 40](takari-builder-enforcer/src/test/Java/io/takari/builder/enforcer/ThreadContextManagerTest.java#L40)

We then may use it to wrap any work we potentially pass to another thread: [ThreadContextManagerTest.java Line 56](takari-builder-enforcer/src/test/Java/io/takari/builder/enforcer/ThreadContextManagerTest.java#L56)

      PolicyContextPreserver preserver = new PolicyContextPreserver();
      Arrays.asList(1,2,3).parallelStream().map( 
        i -> preserver.wrap( new GetPolicyContexts()).call() )
            .collect(Collectors.toSet());

<a id="files-v-classpath"></a>
## URLs vs. Files vs. Classpath Resources

```diff
- WARNING: Just read this. You're probably doing it wrong.
```

Or, how to honor Maven's modules.

- You may only read Java.util.Files from within the folder structure of the Maven project your builder executes in. 

- You may not read Files from other Maven modules on which your Maven module depends within a reactor build. You may read the contents of these modules as URLs in a Maven provided classpath. Read these URLs as input streams.
  - Don't assume you can convert or modify urls passed to you by builder annotated fields.  We may provide custom urls that can not be used to generate files, or otherwise be manipulated. 
  - Don't convert the urls to Files.
  - Remember that those urls might not always be files. 
  - Maven has the right to pass your project a packaged jar. If you are reading resources from modules you depend on, they may be presented in jar form. Potentially even source files may be presented as source jars.

Now, go read about <a href="takari-builder-enforcer/DESIGN.md">how we enforce modularity in a reactor build</a>.   

<a id="recommended-structure"></a>
## Recommended Builder Project Structure

Builder projects follow the conventional Maven project layout:
  
```
+-- pom.xml              # project pom.xml
\-- src
    |-- main
    |   \-- java         # main bulder sources
    \-- test
        |-- java         # builder unit and optionally integration tests
        \-- projects     # integration test projects
```

Builder project pom.xml files must include configuration necessary to automatically generate Maven plugin implementation from @Builder annotations. 

Specifically, the builder project pom.xml file:

* Must have `io.takari.maven.plugins:takari-lifecycle-plugin` plugin with `<extensions>true</extensions>`and the following configuration
  * `<source>1.8</source>` to set Java compilation source/target level to 1.8
  * `<proc>procEX</proc>` to enable annotation processing in "all-or-nothing" mode.
* Must use `takari-maven-plugin` packaging type to enable automatic Maven plugin generation.
* Must dependend on `io.takari.builder:takari-builder` to use @Builder annotations.
* Must dependend on `io.takari.builder:takari-builder-apt` with scope=provided to validate @Builder annotations and generate Maven plugin.

Here is the recommended pom.xml (abbreviated):

```xml
<project ...>

  <parent>
    ...
  </parent>

  <groupId>...</groupId>
  <artifactId>...</artifactId>
  <version>...</version>

  <packaging>takari-maven-plugin</packaging>

  <properties>
    <builder-api.version>...</builder-api.version>
    <takari-lifecycle.version>...</takari-lifecycle.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.takari.builder</groupId>
      <artifactId>takari-builder</artifactId>
      <version>${builder-api.version}</version>
    </dependency>
    <dependency>
      <groupId>io.takari.builder</groupId>
      <artifactId>takari-builder-apt</artifactId>
      <version>${builder-api.version}</version>
      <scope>provided</scope>
    </dependency>
    
    ... other project dependencies

  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>io.takari.maven.plugins</groupId>
        <artifactId>takari-lifecycle-plugin</artifactId>
        <version>${takari-lifecycle.version}</version>
        <configuration>
          <proc>procEX</proc>
        </configuration>
      </plugin>

      ... other plugin configuration

   </build>
```

### Unit Tests are Strongly Recommended

We stronly recommend that Builder projects include at least basic unit tests that validate the given test configuration and input files, and that the Builder produces expected outputs.

## Example Builder Project

[takari-builder-demo](takari-builder-demo) project included in the source tree provides an example of the recommended project structure.

[CopyFilesBuilder.java](takari-builder-demo/src/main/java/io/takari/builder/demo/CopyFilesBuilder.java) implements a build that copies configured input files to the configured output directory. The builder considers 13-byte-long input files invalid (and who doesn't?!) and reports corresponding build error message.

[CopyFilesBuilderTest.java](takari-builder-demo/src/test/java/io/takari/builder/demo/CopyFilesBuilderTest.java) demonstrates how to implement builder unit tests.

Although isn't necessary in most cases, the demo project includes [CopyFilesMavenIntegrationTest.java](takari-builder-demo/src/test/java/io/takari/builder/demo/CopyFilesMavenIntegrationTest.java) that shows how to implement Maven plugin integration tests.

## Example Project That Uses a Builder

Builder projects produce normal Maven plugin jar artifacts and can be directly used in Maven projects like any other Maven plugin. In most cases, however it is necessary to add `takari-builder-enforcer` build extension to the project pom.xml or parent pom. This is a workaround for a limitation in Mavel  classloading model and failure to add this extension will result in `SecurityException`.

```xml
  <plugin>
    <groupId>io.takari.builder</groupId>
    <artifactId>takari-builder</artifactId>
    <version>${takari-builder.version}</version>
    <extensions>true</extensions>
  </plugin>
```
<a id="detailed-annotations-description"></a>
## Incremental @Annotations detailed description (in alphabetical order)

### @Dependencies(scope, transitive)
`@Dependencies` declares that the annotated parameter is the collection of project dependencies and gives the builder access to the project's resolved dependency artifact files or metadata, depending on the target type. For Files, Builder execution is required when there is a change in any of the files that represent the dependency artifacts. For metadata, only if the artifact metadata changes, will the builder be executed.

`scope` attributes defines required dependency resolution scope.

`transitive` if set to false means that only direct dependencies are resolved, otherwise transitive dependencies are also resolved

`@Dependencies` can only be applied to parameters of type `List<File>`, `List<Path>`, `List<IArtifactMetadata>` or `Map<IArtifactMetadata, File>`.

The following is an example on how to use the `@Dependencies` annotation in Java to resolve artifact files:

```Java
@Dependencies(scope=ResolutionScope.COMPILE)
List<File> dependencies;
```

The following is an example on how to use the `@Dependencies` annotation in Java to resolve the metadata:

```Java
@Dependencies(scope=ResolutionScope.COMPILE)
List<IArtifactMetadata> metadatas;
```

The following is an example on how to use the `@Dependencies` annotation in Java to resolve a map of the metadatas to artifact Files:

```Java
@Dependencies(scope=ResolutionScope.COMPILE)
Map<IArtifactMetadata, File> dependencyMap;
```


The `@Dependencies` parameter value is fully derived from project model and does not have corresponding XML configuration.

### @DependencyResources(scope)
`@DependencyResources` declares that the annotated parameter is a collection of resources within a resolved project dependency(s). Builder execution is required when the resource's URL or the resource's contents have changed.

`scope` attribute defines required dependency resolution scope.

`includes`/`excludes` attributes define ant-like resources name matching patterns.

### @ArtifactResources(scope, resourcesRequired, includes, excludes)
`@ArtifactResources` declares that the annotated parameter is a collection of resources within a resolved artifact. Artifacts do not need to be dependencies. Builder execution is required when the resource's URL or the resource's contents have changed.

Note that at the moment, `@ArtifactResources` is currently restricted to only support defining artifacts that are project dependencies.

`@ArtifactResources` supports the following attributes:
* `scope` defines project artifact resolution scope
* `resourcesRequired` if set to true, it means the selected artifact must have at least one matching resource
* `includes`, `defaultIncludes` define resource name includes pattern
* `excludes`, `defaultExcludes` define resource name excludes pattern

`@ArtifactResources` supports `Collection<URL>`, `Collection<IArtifactResources>` target parameter types.

Here is an example of a fully user-configurable artifact resources parameter and corresponding example xml configuration:

```java
@ArtifactResources(resourcesRequired=true, defaultIncludes="test.xml")
List<URL> resources;
```

```xml
<resources>
  <artifact>
    <groupId>group</groupId>
    <artifactId>artifact</artifactId>
  </artifact>
  <includes>test.xml</includes>
</resources>
```

### @GeneratedResourcesDirectory(value, type, includes, excludes)

`@GeneratedResourcesDirectory` declares that an annotated parameter is a generated resources output directory and allows a builder to have a write access to the directory. The builder execution will only occur if there is a change in configuration since the previous execution. The *type* annotation element is used to indicate the type of resources stored at this directory. The value of *type* annotation element could be either `ResourceType.MAIN` or `ResourceType.TEST` and if unspecified, the default value is `ResourceType.MAIN`.

`@GeneratedResourcesDirectory` xml configuration supports `<location>`, `<includes>` and `<excludes>` elements, however the actual elements allowed can be restricted by use of read-only annotation elements in java.

Here is an example of a fully user-configurable generated test resources directory parameter and corresponding example xml configuration:

```java
@GeneratedResourcesDirectory(defaultLocation="${project.build.directory}/generated-resources/wsdls",
  type=ResourceType.TEST, defaultIncludes="**/*.wsdl")
File resources;
```

```xml
<resources>
  <location>${project.build.directory}/generated-sources/wsdls</location>
  <includes>
    <include>**/*Test.wsdl</include>
  </includes>
  <excludes>
    <exclude>**/*DisabledTest.wsdl</exclude>
  </excludes>
</resources>
```

### @GeneratedSourcesDirectory(value)

`@GeneratedSourcesDirectory` declares that an annotated parameter is a generated sources output directory and allows a builder to have write access to the directory. The builder execution will only occur if there is a change in configurtion since the previous execution.

`@GeneratedSourcesDirectory` xml configuration supports setting the value to the path of the directory.

Here is an example of a fully user-configurable generated sources directory parameter and corresponding example xml configuration:

```java
@GeneratedSourcesDirectory(defaultValue="${project.build.directory}/generated-sources")
File sources;
```

```xml
<sources>${project.build.directory}/generated-sources</sources>
```

### @InputDirectory(value, includes, excludes)

`@InputDirectory` declares that the annotated parameter is an input directory and allows the Builder read access to the directory files that match `includes`/`excludes` name patterns. Builder execution is required when any of the matching files are added, changed or removed compared to the previous builder execution.

`filesRequired` attribute, if set to `true`, will guarantee that injected parameter value will be existing directory that contains matching files. If set to `false` (the default), the injected parameter can be `null`, can be directory that does not exist or existing directory that does or does not contain matching files.

`@InputDirectory` supports `File` and `Path` target parameter type, as well as collections and arrays of `File` and `Path`.

For consistency with `@InputDirectoryFiles`, `@InputDirectory` uses the `<location>` XML configuration element.

Here is an example parameter declaration in Java and the corresponding example XML configuration:

```Java
@InputDirectory(defaultLocation="src/main/model", includes="**/*.mdo")
File modelDirectory;
```

```xml
<modelDirectory>
  <location>src/model</location>
</modelDirectory>
```

#### ${project.compileSourceRoots} and ${project.testCompileSourceRoots}

The `${project.compileSourceRoots}` and `${project.testCompileSourceRoots}` expression can be used in `@InputDirectory` to allow reading files from the project's compile source roots. If this expression is used, it can only be set as the `value` or `defaultValue` of the parameter, and cannot be configured in the pom file. In addition, adding additional locations is not supported. The expression will be evaluated to a `List<File>`.

```Java
@InputDirectory(location="${project.compileSourceRoots}", includes="**/*")
public List<File> compileSourceRoots;
```


### `@InputDirectoryFiles` and `@DependencyResources`

`@InputDirectoryFiles` and `@DependencyResources` parameters allow builders access input resources identified by resources containers (filesystem directories or project dependencies) and ant-like resource name patterns. Builder execution is required when any of the matching resources are added, changed or removed compared to the previous builder execution.

> Think of `@InputDirectoryFiles` and `@DependencyResources` as one or more buckets of resources (directories or artifacts) and resources names includes/excludes patterns.

`@InputDirectoryFiles` supports the following attributes

* `value`, `defaultValues` attributes define input directory location.
* `filesRequired` attribute, if set to `true`, means that configured input directory(ies) must exist and must have at least one matching file.
* `includes`, `defaultIncludes` attributes define file name includes pattern.
* `excludes`, `defaultExcludes` attributes and `<excludes>` xml configuration define file name excludes pattern. no files are excluded if neither annotation attributes nor xml configuration are provided.

`@InputDirectoryFiles` supported `Collection<URL>`, `Collection<File>`, `Collection<Path>` and `Collection<IDirectoryFiles>` target parameter types.


`@DependencyResources` supports the following attributes:

* `scope` defines project dependency resolution scope
* `resourcesRequired` attribute, if set to `true`, means that selected dependencies must have at least one matching resources.
* `includes`, `defaultIncludes` attributes define resource name includes pattern. 
* `excludes`, `defaultExcludes` attributes define resoure name excludes pattern. no resources are excluded if neither annotation attributes nor xml configuration are provided.

`@DependencyResources` supports `Collection<URL>`, `Collection<IArtifactResources>` target parameter types. `IArtifactResources` encapsulates artifact metadata, resource name include/excludes patterns and selected resources URLs for a single project dependency.


#### Canonical verbose xml configuration schema

Input directory files:

  ```xml
  <parameterName>
    <files>
      <locations>
        <location>...</location>
        <location>...</location>
      </locations>
      <includes>
        <include>...</include>
        <include>...</include>
      </includes>
      <excludes>
        <exclude>...</exclude>
        <exclude>...</exclude>
      </excludes>
    </files>
    <files>
      <locations>
        ...
      </locations>
      <includes>
        ...
      </includes>
      <excludes>
        ...
      </excludes>
    </files>
  </parameterName>
  ```

Dependency resources:

  ```xml
  <parameterName>
    <resources>
      <dependencies>
        <dependency>
          <groupId>...</groupId>
          <artifactId>...</artifactId>
          <classified>...</classified>
        </dependency>
        <dependency>
          <groupId>...</groupId>
          <artifactId>...</artifactId>
          <classified>...</classified>
        </dependency>
      </dependencies>
      <includes>
        <include>...</include>
        <include>...</include>
      </includes>
      <excludes>
        <exclude>...</exclude>
        <exclude>...</exclude>
      </excludes>
    </resources>
    <resources>
      <dependencies>
        ...
      </dependencies>
      <includes>
        ...
      </includes>
      <excludes>
        ...
      </excludes>
    </resources>
  </parameterName>
  ```


#### Alternative flat-list xml configuration schema

Fully equivalent to the canonical schema, supports the same parameter target types

  ```xml
  <parameterName>
    <files>
      <location>...</location>
      <location>...</location>
      <include>...</include>
      <include>...</include>
      <exclude>...</exclude>
      <exclude>...</exclude>
    </files>
    <files>
      ...
    </files>
  </parameterName>
  ```

  ```xml
  <parameterName>
    <resources>
      <dependency>...</dependency>
      <dependency>...</dependency>
      <include>...</include>
      <include>...</include>
      <exclude>...</exclude>
      <exclude>...</exclude>
    </resources>
    <resources>
      ...
    </resources>
  </parameterName>
  ```

Even simpler configuration, same includes/excludes configuration applies to all resource names

  ```xml
  <parameterName>
    <location>...</location>
    <location>...</location>
    <include>...</include>
    <include>...</include>
    <exclude>...</exclude>
    <exclude>...</exclude>
  </parameterName>
  ```

  ```xml
  <parameterName>
    <dependency>...</dependency>
    <dependency>...</dependency>
    <include>...</include>
    <include>...</include>
    <exclude>...</exclude>
    <exclude>...</exclude>
  </parameterName>
  ```

The simplest form, single directory location or dependency

  ```xml
  <!-- parameter value is the location -->
  <parameterName>...</parameterName>
  ```

  ```xml
  <!-- can also use colon-notation in parameter value, see below -->
  <parameterName>
    <groupId>...</groupId>
    <artifactId>...</artifactId>
    <classified>...</classified>
  </parameterName>
  ```

#### Alternative colon-notation `<dependency>` configuration

```xml
  <resources>
    <dependency>groupId:artifactId:classifier</dependency>
  </resources>
```

#### More specific target parameter types

Parameter type `IDirectoryFiles` (likely the most common case):

  ```xml
  <parameterName>
    <location>...</location> <!-- one and only one -->
    <includes>...</includes>
    <excludes>...</excludes>
  </parameterName>
  ```

Parameter type `IArtifactResources` (for consistency with IDirectoryFiles):

  ```xml
  <parameterName>
    <dependency>...</dependency> <!-- one and only one -->
    <include>...</include>
    <exclude>...</exclude>
  </parameterName>
  ```

#### Interaction between @InputDirectoryFiles/@DependencyResources attributes and xml configuration

If `includes` and/or `excludes` attributes are defined in java @Annotation, corresponding parameter xml configuration cannot have xml `<includes>`/`excludes` elements. `defaultIncludes`/`defaultExcludes` annotation attributes, like the names suggest, provide default configuration values but still allow `<includes>`/`excludes` elements in corresponding parameter configuration.

`@InputDirectoryFile.value` annotation attribute defines input directories locations that cannot be changed with xml configuration. `@InputDirectoryFile.defaultValue`, on the other hand, can be overriden with xml configuration.

#### ${project.compileSourceRoots} and ${project.testCompileSourceRoots}

The `${project.compileSourceRoots}` and `${project.testCompileSourceRoots}` expressions can be used in `@InputDirectoryFiles` to allow reading files from the project's compile source roots. If this expression is used, it can only be set as the `value` or `defaultValue` of the parameter, and cannot be configured in the pom file. In addition, adding additional locations is not supported. The expression will be evaluated to a `List<IDirectoryFiles>`.

```Java
@InputDirectoryFiles(value="${project.compileSourceRoots}", includes="**/*")
public List<IDirectoryFiles> compileSourceRootFiles;
```

### @InputFile

`@InputFile` declares that the annotated parameter is an input file and allows the builder read access to the file or collection of files in the configuration. Builder execution is required when a matching file has changed since the previous execution.

`fileRequired` attribute, if set to `true` guarantees that injected parameter value will be an existing regular file.

`@InputFile` supports `File` and `Path` target parameter types, as well as a collection or array of `File` and `Path` parameter types. The annotation supports setting a `defaultValue` in the case of no XML configuration, or setting a `value` which cannot be overridden by XML configuration.

The supported XML configuration parameter is setting the value to the path of the input file. In the case of a collection, the element will have child elements whose values will be the paths to their respective input files. Using a relative path as a value will resolve it against the project basedir.

Here is an example parameter declaration in Java and the corresponding example XML configuration for a single file:

```Java
@InputFile(defaultValue="src/model/default.txt")
File modelInput;
```

```xml
<modelInput>src/model/input.txt</modelInput>
```

This is a corresponding example for using a collection:

```Java
@InputFile(defaultValue={"src/model/default1.txt", "src/model/default2.txt"})
List<File> modelInputs;
```

```xml
<modelInputs>
  <modelInput>src/model/input1.txt</modelInput>
  <modelInput>src/model/input2.txt</modelInput>
  <modelInput>src/model/input3.txt</modelInput>
</modelInputs>
```


### @OutputDirectory
`@OutputDirectory` declares that the annotated element is an output directory and allows the builder write access to it. Builder execution will only occur if the configuration has changed since the previous execution.

`@OutputDirectory` supports `File` and `Path` target parameter types, as well as a collection or array of `File` and `Path`. The annotation supports setting a `defaultValue` in the case of no XML configuration, or a `value` which cannot be overridden by XML configuration.

The supported XML element should contain a string value representing the path to the directory. In the case of a collection, the element will now contain child elements of any name whose values are strings pointing to the paths of each respective output directory.

Here is an example parameter declaration in Java and the corresponding example XML configuration:

```Java
@OutputDirectory(defaultLocation="target/output")
File outputDirectory;
```

```xml
<outputDirectory>target/output</outputDirectory>
```

Here is a corresponding example for a collection parameter type:

```Java
@OutputDirectory
List<File> outputs;
```

```xml
<outputs>
  <output>target/out1</output>
  <output>target/out2</output>
  <output>target/out3</output>
</outputs>
```

### @OutputFile
`@OutputFile` declares that the annotated element is an output file and allows the builder write access to it. Builder execution will only occur if the configuration has changed since the previous execution.

`@OutputFile` supports `File` and `Path` target parameter types, as well as a collection or array of `File` and `Path`. The annotation supports setting a `defaultValue` in the case of no XML configuration, or a `value` which cannot be overridden by XML configuration.

The supported XML element should contain a string value representing the path to the file. In the case of a collection, the element will now contain child elements of any name whose values are strings pointing to the paths of each respective output file.

Here is an example parameter declaration in Java and the corresponding example XML configuration:

```Java
@OutputFile(defaultLocation="target/generated-resources/paths.txt")
File outputFile;
```

```xml
<outputFile>target/generated-resources/paths.txt</outputFile>
```

Here is a corresponding example for a collection parameter type:

```Java
@OutputFile
List<File> outputFiles;
```

```xml
<outputFiles>
  <outputFile>target/out1.txt</outputFile>
  <outputFile>target/out2.txt</outputFile>
  <outputFile>target/out3.txt</outputFile>
</outputFiles>
```

### @Parameter(value)

`@Parameter` declares annotated element is a builder parameter. The framework injects parameter value (after doing `${property}` expansion, etc) but does not interpret the value in any other way. The builder execution is required when parameter value changes compared to the previous builder execution.

`@Parameter` supports `String`, `File`, `Path`, primivite data types, their collections and arrays.

When used with primitive data types, parameter must use `required=true` annotation attribute and parameter value must be provided either with annotation `value`/`defaultValue` attributes or with XML configuration.

When used with multi-value parameter types, `value`/`defaultValue` attributes can provide comma-separated list of values. For example, the following list parameter will be populated with two elements (`UTF8` and `ASCII`) by default.

```Java
@Parameter(defaultValue="UTF8,ASCII")
List<String> encodings;
```

`@Parameter` also supports custom Java objects that have no-arg public constructors (and their collections and arrays). Such custom data objects can only be configured from XML and the framework will use simple xml-to-Java name mapping. 

For example, the following Java snippet defines `descriptors` parameter of type `List<Configuration>` (note `Configuration#descriptor` field is annotated as `@InputFile`).

```Java
static class Configuration {
  String label;
  @InputFile File file;
}

@Parameter
List<Configuration> descriptors;
```

```xml
<descriptors>
  <descriptor>           <!-- configuration of the first list element -->
    <label>L1</label>    <!-- assigned to Configuration#label member  -->
    <file>l1.txt</file>  <!-- assigned to Configuration#file member   -->
  </descriptor>
  <descriptor>           <!-- configuration of the 2nd list element   -->
    <label>L2</label>
    <file>l2.txt</file>
  </descriptor>
</descriptors>
```

<a id="builder-testing"></a>
## Builder Testing

### Builder Unit Tests

Write your unit tests using the builder testing harness from Java package [`io.takari.builder.testing`](takari-builder/src/main/Java/io/takari/builder/testing) starting with the [`BuilderExecution`](takari-builder/src/main/Java/io/takari/builder/testing/BuilderExecution.java) class interface.

A basic unit test for a Builder does the following:

 - Sets up required test input files.
 - Configures the builder environment using `BuilderExecution` object with required properties and configuration elements.
 - Executes the builder.
 - Verifies the results.

Here's an example test:

```Java
 @Test
  public void testBasic() {
    ...

    BuilderExecutionResult result = 
        BuilderExecution.builderExecution(testProjectBaseDir, ClassWithBuilderAnnotatedMethod.class)
        .withProperty(propertyName, propertyValue)
        .withConfiguration(configurationElementName, configurationElementValue)
        .withConfigurationXml(configurationElementName, configurationXMLValue)
        .execute();

    result.assertNoErrors().assertOutputFiles(testProjectBaseDir, expectedOutputFiles);

    ...
  }
```

### Maven Plugin Integration tests

Although generally not required, Maven plugin integration tests provide an additional measure of confidence that Builders work as designed during a Maven build. [Takari Maven Plugin Testing Framework](https://github.com/takari/takari-plugin-testing-project/blob/master/readme.md#integration-testing) is the recommended way to implement integration tests. The incremental build framework does not impose any additional integration test requirements.

[CopyFilesMavenIntegrationTest](takari-builder-demo/src/test/Java/io/takari/builder/demo/CopyFilesMavenIntegrationTest.java) and corresponding [test project](takari-builder-demo/src/test/projects/basic) provide an example of a builder Maven integration test.

### Maven Plugin Unit Tests

Maven plugin unit test support is provided to simplify migration of existing Maven plugins to takari-builder framework. For new builder implementations builder unit test harness described above should be prefered instead.

Maven plugin unit tests must explicitly manage Builder enforcement context. For tests using JUnit4 add the following `@Rule` to the test 

```Java
  @Rule
  public final BuilderRuntime enforcer = new BuilderRuntime();
```

For tests using JUnit3, call BuilderRuntime `enterTestScope()`/`leaveTestScope()` directly

```Java
  @Override
  public void setName(String name) {
    super.setName(name);

    BuilderRuntime.enterTestScope();
  }
  
  @Override
  protected void tearDown() throws Exception {
    BuilderRuntime.leaveTestScope();

    super.tearDown();
  }
```

[CopyFilesMavenUnitTest](takari-builder-demo/src/test/Java/io/takari/builder/demo/CopyFilesMavenUnitTest.java) provides an example of a plugin unit test that uses JUnit4 and [Takari Maven Plugin Testing Framework](https://github.com/takari/takari-plugin-testing-project/blob/master/readme.md#unit-testing).

<a id="Maven-plugin-migration"></a>
## Migrating Existing Maven plugins
   - Plain Maven plugins
      - invoker-plugin tests
      - apache plugin testing harness
      - takari plugin testing harness
   - Takari incrementalbuild library, v 0.10.x and 0.20.x
   - Sonatype/plexus "BuildContext"

These are basic steps and examples for converting an existing Maven Plugin to an incremental builder.

### 1) Update the project's pom.xml file to use the Incremental Build Framework.

Refer to [the list of required pom updates](#recommended-builder-project-structure) for more details.

Additionally, remove dependencies on `org.apache.maven:maven-plugin-api`, `org.apache.maven.plugin-tools:maven-plugin-annotations`, and any other dependencies required by the Maven Plugin API. These dependencies cannot be used by the API, and removing them makes it more clear which parts of the existing code will need to be reworked.

### 2) Changing @Mojo to @Builder

A `@Mojo` class will need to be changed into a `@Builder` method. Generally, this would mean converting a `@Mojo` class's `execute` method into a `@Builder` method along with removing extending from `AbstractMojo`. A single class can have multiple `@Builder` methods. If the `@Builder` methods share the same configuration, it may be wise to put them in the same class. Otherwise, it may make sense to keep the methods separate if configuration is too different from each other.

#### Example Conversion:

Given a `@Mojo`:

```Java
@Mojo(name = "sample", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true)
public class SampleMojo extends AbstractMojo {
  public void execute() {
    // Do stuff
  }
}
```

The corresponding `@Builder` implementation could be:

```Java
public class SampleBuilder {
  @Builder(name = "sample", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresProject = true)
  public void sample() {
    // Do stuff
  }
}
```
### 3) Updating @Parameter to use new Incremental Framework implementation

#### Example Conversion:

All `@Parameter` configurations will need to be converted into the incremental builder's implementation of `@Parameter` or other incremental builder annotations. References to `LifecyclePhase` and `ResolutionScope` will need to be changed to the incremental builder's implementation. Depending on the purpose of the `@Parameter`, it may be wise to take advantage of more specific annotations such as `@InputFile`, `@InputDirectory`, or `@OutputDirectory`.

Given the following `@Parameter` configuration of a `@Mojo`:

```Java
import org.apache.maven.plugin.descriptor.Parameter;

@Parameter(required = true)
File input;

@Parameter
File importDirectory;

@Parameter(defaultValue = "${project.build.directory}/generated")
File outputDirectory;

@Parameter(defaultValue = "Test Message")
String testMessage;
```

The corresponding `@Parameter` configuration of a `@Builder` could be:

```Java
import io.takari.builder.Parameter;

@InputFile
File input;

@InputDirectory(required = false)
File importDirectory;

@OutputDirectory(defaultValue = "${project.build.directory}/generated")
File outputDirectory;

@Parameter(defaultValue = "Test Message")
String testMessage;
```

#### Limitations of New @Parameter Implementation

##### MavenProject

The Incremental Builder Framework does not give access to the entire `MavenProject` context to a `@Builder`. Logic utilizing the `MavenProject` object or more properties will need to be reworked.
