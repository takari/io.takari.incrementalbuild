# Introduction to Takari incrementalbuild library 0.20.x

Incremental build is a build performance optimization, which results in faster builds when the same source tree is repeatedly built multiple times with only some parts of the source tree change between the builds.

---

## Why incremental build is important

* Single non-incremental builder taints entire build. Because the builder can leave obsolete outputs, the only way to guarantee correct build results is to run clean/full build.
* In many/most cases, builder outputs are used as inputs for other builders. For example, generated `.java` sources are used by compiler to generate `.class` files, which are in turn used to generate `.jar` file. Peformance implications of a non-incremental builder are usually far greater than the cost of running the builder itself.
* Use of non-incremental builders makes the project incompatible with m2e.

---

## Basic concepts

---

A **builder** is a piece of build logic that processes zero, one or more input resources (or just "**inputs**") and generates one or more output resources (or "**outputs**"). For example, a java compiler is a builder that processes `.java` files and compile classpath as inputs and generates `.class` files as outputs. Another example is a builder that takes `.class` and other resources and generates a `.jar` file.

Builder inputs are usually files on filesystem, but in some cases it may be convenient to use other input types, like zip-file entries identified by URL. Builder outputs are always files on filesystem.

---

**Builder configuration** controls builder behaviour. For example, javac compiler `-target` parameter controls format of generated `*.class` outputs. Typically, builder configuration changes much less frequently than builder inputs.

Bulder inputs are most often defined by builder configuration. Configuration can explicitly list all inputs one by one; or configuration can specify base directory and includes/excludes patterns used to locate inputs on filesystem.

---

An **incremental build** is a repeated build of a source tree when outputs of the previous build are still present on filesystem. Conversely, a **clean** build, is a build of a source tree when no outputs of the previous build are present on filesystem.

---

Given the same inputs and configuration, a **reproducible builder** generates the same outputs. TODO: "reproducible builder" does not sound right, maybe we should call them "idemponent builders"?. 

Reproducible builder can skip generation of an output, if inputs that were used to generate the output did not change since the previous build.

---

An **incremental builder** is a reproducible builder that skips generation of outputs if their corresponding inputs did not change since the previous build.

A **fine-grained incremental builder** skips generation of individual outputs (or **carries over** individual outputs) if corresponding inputs did not change since the previous build. A **coarse-grained incremental builder** regenerates all outputs if any of its inputs changed since previous build.

In most cases builder implementation change and builder configuration change results in **build escalation**, when the builder will unconditionally (re)process all inputs and (re)generate all outputs regardless if inputs did or did not change since the previous build.

---

An **aggregating builder** processes inputs identified by base directory and includes/excludes pattern and generate single output. The output can aggregate actual inputs contents. For example, a zip file aggregates contents of all inputs. Alternatively, the output can aggregate some metadata about the inputs. For example, `META-INF/services` provider-configuration file aggregates class names of all service types.

---

An output is called "**orphaned**" if its corresponding inputs were removed from the source tree since the previous build. An output is called "**stale**" if its corresponding inputs were changed in such a way that the output is no loger produced during a clean build. Both stale and orphaned outputs must be removed during incremental build. (TODO "stale" does not sound right).

In addition to output resources, builders can produce **build messages** associated with individual inputs. Such messages as well as overall build success/failure indication must be correctly managed during incremental build. For example, java compiler can produce compilation error message if a `.java` input cannot be compiled and indicated that compilation has failed. Subsequent incremental build of the same unmodified source tree is expected to fail and produce the same compiler error message. Obviously, the error message must be cleared and the build must succeed if the problem was fixed in the `.java` input since the previous build.

---

## Takari Incremental Build Library

Takari Incremental Build library is a set of APIs that help implement some common aspects of incremental builder logic.

At its core, the library allows builders persist metadata about inputs and outputs from one build to the next. The persisted metadata can then be used to determine which inputs require (re)processing and to remove obsolete outputs.

---

Although the library API is designed to work with any build tool, there are several features that are currently implemented for Apache Maven only:

* automatic detection of builder implementation and configuration changes and 
  corresponding build escalation
* automatic removal of obsolete outputs
* eclipse/m2e workspace build support

---

The library provides the following key API types

* `ResourceStatus` represents resource status compared to the previous build. One of `NEW`, `MODIFIED`, `UNMODIFIED` or `REMOVED`.
* `ResourceMetadata` represents build input or output resource metadata. It provides methods to query resource status compared to the previous build, to get the resource (typically, a File) and to indicate that the resource is being processed by the builder (see context-specific notes below). Note that the resource may or may not be present on filesystem.
* `Resource` represents an input resource that is being processed by the builder (see context-specific notes below). In addition to ResourceMetadata methods, this interface provides methods to add resource messages and associate outputs.
* `Output` represents an output resource being generated by the builder. In addition to ResourceMetadata and Resource methods, this interface provides methods to open new OutputStream to write to the resource. Note that returned OutputStream implementation will not "touch" the file it the new contents is identical to the old.

---

All interaction with the library starts with a **build context**. Three build context implementations are provided as part of the library (see below). It is also possible to develop custom build context implementations, but this is beyond the scope of this tutorial.

---

### Using Takari Incremental Build Library with Maven

In Apache Maven, builders are called "mojos", which is short for "Maven plain Old Java Object". Mojos must implement `org.apache.maven.plugin.Mojo` interface, although in practice most mojos extend `org.apache.maven.plugin.AbstractMojo` abstract class. 

Builder configuration paramaters are declared as mojo fields annotated with `@Parameter`. Parameter values are provided as pom.xml `<configuration>` elements; it is also possible to specify mojo parameters as `-Dproperties` during Maven invocation.

---

### BuildContext

`BuildContext` supports implementation of fine-grained incremental builders that generate each output from one and only one input, but each input can be used to generate zero, one or more outputs.

At high-level, a builder that uses BuildContext is expected to implement the following build steps

1. Register all inputs with the build context by calling `BuildContext#registerInputs()`
2. For each input that requires processing
   1. Indicate that the resource is being processed by calling `ResourceMetadata#process()`
   2. Associate the generated output(s) with the input by calling one of `Resource#associateOutput` methods.

Note that for convenience, steps 1. and 2. can be implemented by single `BuildContext#registerAndProcessInputs()` method invocation.

---

Here is skeleton implementation of `Mojo#execute()` method. See [CopyFilesMojo](src/main/java/com/ifedorenko/incrementalbuild/tutorial/CopyFilesMojo.java) for complete Mojo example. See [CopyFilesMojoTest](src/test/java/com/ifedorenko/incrementalbuild/tutorial/CopyFilesMojoTest.java) for corresponding Mojo unit test.

```java
// (1) register all inputs and determine inputs that require processing
for (Resource<File> input : context.registerAndProcessInputs(dir, includes, excludes)) {
  File inputFile = input.getResource();

  File outputFile = getOutputFile(inputFile);
  
  // (2) generate outputs
  try (OutputStream os = input.associateOutput(outputFile).newOutputStream()) {
    // write to the output stream
  }
}

// (3) the build context automatically removes obsolete outputs
```

---

### BasicBuildContext

`BasicBuildContext` supports implementation of coarse-grained incremental builders. It is useful for builders that operate on inputs that are explicitly provided via builder configuration. Here is skeleton `Mojo#execute()` method implementation.

```java
// (1) register all inputs with the build context
context.registerInput(input);

// (2) determine if processing of the inputs is required
if (context.isProcessingRequired()) {

   // (3) generate the output(s)
   try (OutputStream os = context.processOutput(output).newOutputStream()) {
     // write the output
   }
}
```

---

`BasicBuildContext` can also be used to implement better-than-nothing wrapper around builder logic that is not possible or not practical to implement as fine-grained incremental builders. Here is skeleton `Mojo#execute()` method that shows how to provide coarse-grained incremental build wrapper for thirdparty `BlackBoxBuilder` builder.

```java
// (1) register all inputs with the build context
for (File input : inputs) {
  context.registerInput(input);
}

// (2) determine if processing of the inputs is required
if (context.isProcessingRequired()) {

   // (3) invoke BlackBoxBuilder to do the actual work
   BlackBoxBuilder builder = new BlackBoxBuilder(inputs);
   builder.execute();
   
   // (4) register outputs with the build context
   for (File output : builder.getOutputs()) {
     context.processOutput(output);
   }
}

// (5) the build context automatically removes obsolete outputs
```

Although coarse-grained incremental builder wrapper is easier to implement than proper fine-grained incremental builder, it may not provide adequate performance during m2e incremental build.

---

### AggregatorBuildContext (input metadata)

Input metadata aggregation is multi-step process.

1. Builder registers all inputs to be aggreated with the build context.
2. Each input that was changed since previous build is processed and input metadata is collected in intermediate persisted metadata map. 
3. If collected metadata is different compared to the previous build, the metadata is then written to the output.

![input metadata aggregation](aggregate-metadata.svg)

Collected metadata is a String->Serializable map, but the library does not make any further assumptions about map keys or values.

---

Unlike other build context types, `AggregatorBuildContext` encapsulates overall aggregation implementation and builder-specific behaviour is provided with `MetadataAggregator<T>` callback interface. The interface has two methods

* `Map<String, T> glean(File input)` gleans metadata from single input
* `void aggregate(Output<File> output, Map<String, T> metadata)` generates aggregate output

---

Here is skeleton `Mojo#exectute()` method that aggregates inputs metadata

```java
InputSet inputs = context.newInputSet();
    
// (1) register inputs with the input set
inputs.addInputs(from, includes, excludes);
    
inputs.aggregateIfNecessary(output, new MetadataAggregator<String>() {

  // (2) glean metadata
  @Override
  public Map<String, String> glean(File input) {
    ...
  }

  // (3) generate aggregate output
  @Override
  public void aggregate(Output<File> output, Map<String, String> metadata) {
    ...
  }

}
```

---

## Testing

* Initial build. Assert all expected outputs are generated.
* No-change rebuild. Assert all outputs are carried over.
* Rebuild after a new input was introduced. Assert new outputs are generated and the rest are carried over.
* Rebuild after an input was removed. Assert orphaned outputs are removed and the rest are carried over.


---

<!--
 | Copyright (c) 2015-2016 Igor Fedorenko
 | All rights reserved. This program and the accompanying materials
 | are made available under the terms of the Eclipse Public License v1.0
 | which accompanies this distribution, and is available at
 | http://www.eclipse.org/legal/epl-v10.html
 |
 | Contributors:
 |      Igor Fedorenko - initial API and implementation
-->