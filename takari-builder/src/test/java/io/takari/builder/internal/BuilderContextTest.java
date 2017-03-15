package io.takari.builder.internal;

import static io.takari.maven.testing.TestResources.create;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.helpers.NOPLogger;

import io.takari.builder.enforcer.ComposableSecurityManagerPolicy;
import io.takari.builder.enforcer.PolicyContextPreserver;
import io.takari.builder.internal.workspace.FilesystemWorkspace;

public class BuilderContextTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private BuilderContext.Builder newBuilder() {
    return BuilderContext.builder(NOPLogger.NOP_LOGGER, "test", temp.getRoot().toPath(), null,
        new BuilderWorkspace(new FilesystemWorkspace(), temp.getRoot().toPath(), null));
  }

  @Test
  public void testReadNonExistingFiles() throws Exception {
    File basedir = temp.getRoot();

    BuilderContext testee = newBuilder().build();

    // assert reads of files that do not exist are still allowed
    assertTrue(testee.checkRead(new File(basedir, "file/does/not/exist").getAbsolutePath()));
  }

  @Test
  public void testReadAfterWrite() throws Exception {
    File basedir = temp.getRoot();

    BuilderContext testee = newBuilder().addOutputDirectory(basedir.toPath()).build();

    File file = new File(basedir, "file").getCanonicalFile();
    assertTrue(testee.checkAndRecordWrite(file.getAbsolutePath()));

    Files.createFile(file.toPath());

    // assert reads of files that have been written during this build are allowed
    assertTrue(testee.checkRead(file.getAbsolutePath()));
  }

  @Test
  public void testReadDirectories() throws Exception {
    File basedir = temp.newFolder();
    File file = new File(basedir, "1.txt");
    file.createNewFile();

    BuilderContext testee = newBuilder().addInputFile(file.toPath()).build();
    assertTrue(testee.checkRead(file.getParent()));
  }

  @Test
  public void testWriteExistingFiles() throws Exception {
    // the point of this test is to assert builders can't write existing files

    // obsolete files cleanup assumes files written by a builder can be deleted
    // but
    // if the file existed before it was written by the builder, there is no good way
    // to implement the cleanup if the builder stops writing the file in the future

    File output = temp.newFolder();

    BuilderContext testee = newBuilder() //
        .addOutputDirectory(output.toPath()) //
        .build();

    // assert can't write existing files
    create(output, "a");
    assertFalse(testee.checkAndRecordWrite(new File(output, "a").getAbsolutePath()));
  }

  @Test
  public void testWriteMultipleTimes() throws Exception {
    // assert it is possible to write the same output file multiple times

    File output = temp.newFolder();

    BuilderContext testee = newBuilder() //
        .addOutputDirectory(output.toPath()) //
        .build();

    File file = new File(output, "a").getAbsoluteFile();

    assertTrue(testee.checkAndRecordWrite(file.getAbsolutePath()));
    assertTrue(file.createNewFile());
    assertTrue(testee.checkAndRecordWrite(file.getAbsolutePath()));
  }

  @Test
  public void testExcludesRoot() throws Exception {
    // assert reads and writes outside of configured paths are not allowed

    BuilderContext testee = newBuilder() //
        .addInputFile(temp.newFile().toPath()) //
        .addOutputDirectory(temp.newFolder().toPath()) //
        .build();

    File file = new File(temp.getRoot(), "1.txt");
    file.createNewFile();
    assertFalse(testee.checkRead(file.getAbsolutePath()));
    assertFalse(testee.checkAndRecordWrite(file.getAbsolutePath()));
  }

  @Test
  public void testCreateExistingOutputDirectory() throws Exception {
    File dir = temp.newFolder().getCanonicalFile();

    BuilderContext testee = newBuilder() //
        .addOutputDirectory(dir.toPath()) //
        .build();

    assertTrue(testee.checkAndRecordWrite(dir.getCanonicalPath()));
  }

  @Test
  public void testExec() throws Exception {

    BuilderContext testee = newBuilder() //
        .addExecExceptions(Arrays.asList("git")).build();

    assertTrue(testee.checkExec("git"));
  }

  @Test
  public void testNetworkAccess() throws Exception {

    BuilderContext testee = newBuilder() //
        .setNetworkAccessAllowed(true).build();

    assertTrue(testee.checkSockets());
  }

  @Test
  public void testReadExceptions() throws Exception {

    File basedir = temp.getRoot();
    File test = new File(basedir, "test.txt");
    File notest = new File(basedir, "notest.txt");

    test.createNewFile();
    notest.createNewFile();

    BuilderContext testee = newBuilder().addReadExceptions(Arrays.asList("**/test.txt")).build();
    assertTrue(testee.checkRead(test.getCanonicalPath()));
    assertFalse(testee.checkRead(notest.getCanonicalPath()));
  }

  @Test
  public void testDirectoryReadExceptions() throws Exception {

    File basedir = temp.getRoot();
    File test = new File(basedir, "test");
    File testFile = new File(test, "file.txt");

    test.mkdir();
    testFile.createNewFile();

    BuilderContext testee = newBuilder().addReadExceptions(Arrays.asList("**/test/**")).build();
    assertTrue(testee.checkRead(testFile.getCanonicalPath()));
  }

  @Test
  public void testExtensionsReadExceptions() throws Exception {

    File basedir = temp.getRoot();
    File test = new File(basedir, "test");
    File testFile = new File(test, "file.txt.hello");
    File noTestFile = new File(test, "file.txt");

    test.mkdir();
    testFile.createNewFile();
    noTestFile.createNewFile();

    BuilderContext testee =
        newBuilder().addReadExceptions(Arrays.asList("**/test/file.txt.*")).build();
    assertTrue(testee.checkRead(testFile.getCanonicalPath()));
    assertFalse(testee.checkRead(noTestFile.getCanonicalPath()));
  }

  @Test
  public void testConcurrentReadWriteCheck() throws Exception {
    // the test simply runs checkRead/fileExists/checkWrite/write sequence
    // on multiple threads to stress synchronization implementation. if
    // the test fails, it proves there incorrect synchronization.
    // passing test, however, does not prove correct synchronization.

    final File dir = temp.newFolder().getCanonicalFile();

    final BuilderContext ctx = newBuilder().addOutputDirectory(dir.toPath()).build();

    final int fileCount = 1000;
    final int threadCount = 32;
    final AtomicInteger fileNo = new AtomicInteger();
    final AtomicReference<String> failed = new AtomicReference<>();

    final CyclicBarrier barrier = new CyclicBarrier(threadCount, () -> {
      int no = fileNo.incrementAndGet();
      if (failed.get() != null) {
        // break the barrier
        throw new RuntimeException("file " + no + ": " + failed.get());
      }
    });

    final Runnable r = () -> {
      try {
        int no;
        while ((no = fileNo.get()) < fileCount) {
          File file = new File(dir, Integer.toString(no));
          try {
            barrier.await();
            String path = file.getCanonicalPath();
            if (!ctx.checkRead(path)) {
              failed.set("!ctx.checkRead");
            }
            if (!ctx.checkAndRecordWrite(path)) {
              failed.set("!ctx.checkAndRecordWrite");
            }
            new FileOutputStream(file).close();
            if (!file.exists()) {
              failed.set("!file.exists");
            }
          } catch (IOException e) {
            e.printStackTrace();
            failed.set(e.getMessage());
          } catch (RuntimeException | Error e) {
            e.printStackTrace();
            throw e;
          }
        }
      } catch (InterruptedException | BrokenBarrierException e) {
        // exit the thread
      }
    };

    // Stopwatch sw = Stopwatch.createStarted();
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      Thread thread = new Thread(r, "test worked #" + i);
      threads.add(thread);
      thread.start();
    }

    for (Thread thread : threads) {
      thread.join();
    }

    assertNull(failed.get());

    // double elapsed = sw.elapsed(TimeUnit.MILLISECONDS);
    // System.out.printf("files %d, threads %d, rate %f ms\n", fileCount, threadCount,
    // elapsed / (fileCount * threadCount));
  }

  @Test
  public void testStaleContext() throws Exception {
    ComposableSecurityManagerPolicy.setSystemSecurityManager();

    try {
      BuilderContext testee = newBuilder() //
          .addInputFile(temp.newFile().toPath()) //
          .addOutputDirectory(temp.newFolder().toPath()) //
          .build();

      Path file = temp.getRoot().toPath().resolve("1.txt");
      Files.createFile(file);

      testee.enter();

      // this remembers currently active build context
      PolicyContextPreserver preserver = new PolicyContextPreserver();

      testee.leave();

      try {
        // this validates file system access with remembered and now closed context
        preserver.wrap(() -> Files.readAllBytes(file)).call();
        fail();
      } catch (IllegalStateException expected) {
        // out-of-scope context rejects all operations
      }
    } finally {
      ComposableSecurityManagerPolicy.removeSystemSecurityManager();
    }
  }
}
