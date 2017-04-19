## Builder Classloading

[Takari Maven Classloading](http://takari.io/book/91-maven-classloading.html) document provides an overview of classloaders created during Maven build and how these classloaders are wired together.


### The Pieces Of The Puzzle

Java SecurityManager extension/composition API must be shared by all Build Extensions and Maven Plugins used in the same JVM.

Modularity Enforcer must be able to "wrap" individual subproject builds, which implies it must be loaded either as Maven Core Extension or Build Extension. Modularity Enforcer depends on the composable security manager.

Although not a hard requirement, it is desired to use shared Builder Framework classes. Shared classes will make overall build analysis and enforment (e.g., input-output cycle detection) easier to implement. It is also better aligned with "builder runtime" vision (as opposed to "builder library") and will force us to provide strong API and runtime compatibility from the get-go. Builder Framework depends on the composable security manager.

Maven Plugins are loaded in separate classloaders. The plugins depend on Builder Framework.

exec-maven-plugin needs to access Modularity Enforcer classes. The plan is to remove the plugin after everything is converted to the Builder Framework, but this is not a hard requirement.

Although not immediately needed, surefire-maven-plugin support will require Builder Framework support external JVM fork. This will require surefire plugin access to Builder Framework classes.

### Proposed classloader configuration

Load SecurityManager, Modularity Enforcer and Builder Framework as single Maven Build Extension. In practical terms, this means that **all** Maven projects that use the Builder Framework will need to add the following `<plugin>` element in their parent pom:

```xml
<plugin>
  <groupId>io.takari.builder</groupId>
  <artifactId>takari-builder</artifactId>
  <version>${takari-builder.version}</version>
  <extensions>true</extensions>
</plugin>
```

The build extension will export SecurityManager, Modularity Enforcer and Builder Framework API packages and artifacts. This will ensure that all plugins used by the build will use the same classes from the build extension.



### Eclipse complicates things... or maybe not

As of version 1.7, Eclipse/m2e does not have concept of "reactor build" and all workspace projects are treated independently. There are still workspace-level plugin and extension realm caches, so "equal" build extensions are only loaded by single shared classloader. There are no reactor-build callbacks, but this will be a problem regardless of classloader arrangement.
