/*******************************************************************************
 * Copyright (c) 2015-2016 Igor Fedorenko
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Igor Fedorenko - initial API and implementation
 *******************************************************************************/
package com.ifedorenko.incrementalbuild.tutorial;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.aggregator.AggregatorBuildContext;
import io.takari.incrementalbuild.aggregator.InputSet;
import io.takari.incrementalbuild.aggregator.MetadataAggregator;

@Mojo(name = "line-count", threadSafe = true)
public class LineCountMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.basedir}/src")
  private File from;

  @Parameter(defaultValue = "**/*")
  private Collection<String> includes = Collections.singleton("**/*");

  @Parameter
  private Collection<String> excludes;

  @Parameter(defaultValue = "${project.basedir}/target/counts.txt")
  private File output;

  @Component
  private AggregatorBuildContext context;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    try {
      InputSet inputs = context.newInputSet();
      inputs.addInputs(from, includes, excludes);
      inputs.aggregateIfNecessary(output, new MetadataAggregator<String>() {

        @Override
        public Map<String, String> glean(File input) throws IOException {
          return Collections.singletonMap(input.getName(),
              lineCount(input) + ":" + input.getName());
        }

        @Override
        public void aggregate(Output<File> output, Map<String, String> metadata)
            throws IOException {
          LineCountMojo.this.aggregate(output, metadata);
        }
      });
    } catch (IOException e) {
      throw new MojoExecutionException("Could not count inputs lines", e);
    }
  }

  protected String lineCount(File input) throws IOException {
    try (Stream<String> lines = Files.lines(input.toPath())) {
      return Long.toString(lines.count());
    }
  }

  private void aggregate(Output<File> output, Map<String, String> metadata) throws IOException {
    TreeSet<String> counts = new TreeSet<>(metadata.values());
    try (BufferedWriter out =
        new BufferedWriter(new OutputStreamWriter(output.newOutputStream(), "UTF-8"))) {
      for (String count : counts) {
        out.write(count);
        out.newLine();
      }
    }
  }

}
