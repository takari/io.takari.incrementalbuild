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

import static io.takari.maven.testing.TestResources.assertFileContents;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

public class LineCountMojoTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule maven = new IncrementalBuildRule();

  @Test
  public void testBasicIncremental() throws Exception {
    // TODO there is no real need to test incremental behaviour of metadata aggregating mojo,
    // this behaviour is fully encapsulated in AggregatorBuildContext, which is tested already.
    // it is okay to have single "basic" tests that verifies mojo generates expected output

    File basedir = resources.getBasedir("line-count");

    // clean build
    maven.executeMojo(basedir, "line-count");
    maven.assertBuildOutputs(basedir, "target/counts.txt");
    assertFileContents("3:r1.txt\n5:r2.txt\n", basedir, "target/counts.txt");

    // no-change rebuild
    maven.executeMojo(basedir, "line-count");
    maven.assertCarriedOverOutputs(basedir, "target/counts.txt");

    // introduce new input
    new File(basedir, "src/r3.txt").createNewFile();
    maven.executeMojo(basedir, "line-count");
    maven.assertBuildOutputs(basedir, "target/counts.txt");
    assertFileContents("0:r3.txt\n3:r1.txt\n5:r2.txt\n", basedir, "target/counts.txt");

    // delete an output
    new File(basedir, "src/r2.txt").delete();
    maven.executeMojo(basedir, "line-count");
    maven.assertBuildOutputs(basedir, "target/counts.txt");
    assertFileContents("0:r3.txt\n3:r1.txt\n", basedir, "target/counts.txt");
  }
}
