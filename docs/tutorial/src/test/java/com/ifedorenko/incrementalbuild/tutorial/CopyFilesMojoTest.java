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

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;

public class CopyFilesMojoTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public final TestResources resources = new TestResources();

  @Rule
  public final IncrementalBuildRule maven = new IncrementalBuildRule();

  @Test
  public void testBasicIncremental() throws Exception {
    File basedir = resources.getBasedir("copy-resources");

    // initial build
    maven.executeMojo(basedir, "copy-resources");
    maven.assertBuildOutputs(basedir, "target/r1.txt", "target/r2.txt");

    // no-change rebuild
    maven.executeMojo(basedir, "copy-resources");
    maven.assertCarriedOverOutputs(basedir, "target/r1.txt", "target/r2.txt");

    // introduce new input
    Assert.assertTrue(new File(basedir, "src/r3.txt").createNewFile());
    maven.executeMojo(basedir, "copy-resources");
    maven.assertBuildOutputs(basedir, "target/r3.txt");
    maven.assertCarriedOverOutputs(basedir, "target/r1.txt", "target/r2.txt");

    // delete an input
    Assert.assertTrue(new File(basedir, "src/r1.txt").delete());
    maven.executeMojo(basedir, "copy-resources");
    maven.assertDeletedOutputs(basedir, "target/r1.txt");
    maven.assertCarriedOverOutputs(basedir, "target/r2.txt", "target/r3.txt");
  }

  @Test
  public void testMessages() throws Exception {
    File basedir = resources.getBasedir();
    File src = new File(basedir, "src");
    src.mkdirs();

    // generate an invalid resource
    try (OutputStream out = new FileOutputStream(new File(src, "r1.txt"))) {
      out.write(new byte[13]);
    }

    // initial build
    try {
      maven.executeMojo(basedir, "copy-resources");
    } catch (MojoExecutionException e) {
      Assert.assertTrue(e.getMessage().contains("invalid file length"));
    }
    maven.assertBuildOutputs(basedir, new String[0]);
    maven.assertMessages(basedir, "src/r1.txt", "ERROR r1.txt [0:0] invalid file length");

    // no-change rebuild, expect the same failure and the same error message
    try {
      maven.executeMojo(basedir, "copy-resources");
    } catch (MojoExecutionException e) {
      Assert.assertTrue(e.getMessage().contains("invalid file length"));
    }
    maven.assertBuildOutputs(basedir, new String[0]);
    maven.assertMessages(basedir, "src/r1.txt", "ERROR r1.txt [0:0] invalid file length");

    // fix the build by change input file length
    try (OutputStream out = new FileOutputStream(new File(src, "r1.txt"))) {
      out.write(new byte[12]);
    }
    maven.executeMojo(basedir, "copy-resources");
    maven.assertMessages(basedir, "src/r1.txt", new String[0]);
    maven.assertBuildOutputs(basedir, "target/r1.txt");
  }
}
