# zero or more inputs, each input is processed into one-or-more outputs

## highlights
* inputs are identified by basedir+includes+excludes "sourceroot" configuration
* more than one sourceroot is possible
* only new or modified inputs are processed during each build, 
  implementation is "properly incremental" in other words.
* obsolete and orphaned outputs are detected and removed

## annotated source sketch

    // builder code
    // sourceRoots is parameter passed in, context is injected
    for (InputMetadata<File> metadata
        : context.registerInputs(root.basedir, root.includes, root.excludes) { // (1)
      Input<File> input = metadata.process();                                  // (2)
      File outputFile = ...;
      Output<File> output = input.associateOutput(outputFile);                 // (3)
      try (OutputStream os = output.newOutputStream()) {
      }
    }

    // context #commit logic
    delete orphaned outputs                                                    // (4)
    delete obsolete outputs                                                    // (5)


(1) walks basedir and registers matching inputs, returns new and modified inputs.
    by knowing all registered inputs it is possible to determine what inputs were
    removed since previous build.

(2) marks input as processed during this build.

(2a) if input is not processed, its associated outputs are retained.
     unmodified registered inputs are not processed.

(2b) if input is processed, its old outputs become obsolete and will be deleted
     unless associated with the input during this build

(3) associates generated output file with the input.

(4) outputs become orphaned when their associated inputs are no longer registered,
    which happens when inputs are removed from filesystem, sourceroot includes/excludes
    globs change or when source is removed from build configuration.

(5) outputs become obsolete when they are no longer associated with an input

## CPU, RAM and IO usage estimates during command line build

(1) full directory walk

(2) 

# zero-or-more inputs are processed into one output

# skip if there is no change