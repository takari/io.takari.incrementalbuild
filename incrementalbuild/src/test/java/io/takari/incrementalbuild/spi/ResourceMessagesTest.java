package io.takari.incrementalbuild.spi;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.takari.incrementalbuild.MessageSeverity;

public class ResourceMessagesTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private class TestBuildContext extends DefaultBuildContext {
    protected TestBuildContext() throws IOException {
      super(new FilesystemWorkspace(), new File(temp.getRoot(), "buildstate.ctx"),
          Collections.<String, Serializable>emptyMap(), null);
    }

    public void commit() throws IOException {
      super.commit(null);
    }

    public List<Message> getMessages(File resource) {
      Collection<Message> messages = getState(resource).getResourceMessages(resource);
      return messages != null ? new ArrayList<>(messages) : null;
    }
  }

  private TestBuildContext newBuildContext() throws IOException {
    return new TestBuildContext();
  }

  @Test
  public void testInputMessages() throws Exception {
    File inputFile = temp.newFile("inputFile");

    // initial message
    TestBuildContext context = newBuildContext();
    DefaultResourceMetadata<File> metadata = context.registerInput(inputFile);
    inputFile = metadata.getResource();
    DefaultResource<File> input = metadata.process();
    input.addMessage(0, 0, "message", MessageSeverity.WARNING, null);
    context.commit();

    // the message is retained during no-change rebuild
    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    List<Message> messages = context.getMessages(inputFile);
    Assert.assertEquals(1, messages.size());
    Assert.assertEquals("message", messages.get(0).message);
    context.commit();

    // the message is retained during second no-change rebuild
    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    messages = context.getMessages(inputFile);
    Assert.assertEquals(1, messages.size());
    Assert.assertEquals("message", messages.get(0).message);
    context.commit();

    // new message
    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    input = metadata.process();
    input.addMessage(0, 0, "newMessage", MessageSeverity.WARNING, null);
    context.commit();
    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    messages = context.getMessages(inputFile);
    Assert.assertEquals(1, messages.size());
    Assert.assertEquals("newMessage", messages.get(0).message);
    context.commit();

    // removed message
    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    input = metadata.process();
    context.commit();
    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    Assert.assertNull(context.getMessages(inputFile));
    context.commit();
  }

  @Test
  public void testInputMessages_nullMessageText() throws Exception {
    File inputFile = temp.newFile("inputFile");

    // initial message
    TestBuildContext context = newBuildContext();
    DefaultResourceMetadata<File> metadata = context.registerInput(inputFile);
    inputFile = metadata.getResource();
    DefaultResource<File> input = metadata.process();
    input.addMessage(0, 0, null, MessageSeverity.WARNING, null);
    context.commit();

    context = newBuildContext();
    metadata = context.registerInput(inputFile);
    List<Message> messages = context.getMessages(inputFile);
    Assert.assertEquals(1, messages.size());
    Assert.assertEquals(null, messages.get(0).message);
    context.commit();
  }

  @Test
  public void testExcludedInputMessageCleanup() throws Exception {
    File inputFile = temp.newFile("inputFile");

    // initial message
    TestBuildContext context = newBuildContext();
    DefaultResourceMetadata<File> metadata = context.registerInput(inputFile);
    inputFile = metadata.getResource();
    DefaultResource<File> input = metadata.process();
    input.addMessage(0, 0, "message", MessageSeverity.WARNING, null);
    context.commit();

    // input is removed from input set, make sure input messages are cleaned up
    final List<Object> cleared = new ArrayList<>();
    newBuildContext().commit(new MessageSinkAdaptor() {
      @Override
      public void record(Map<Object, Collection<Message>> allMessages,
          Map<Object, Collection<Message>> newMessages) {
        Assert.assertTrue(allMessages.isEmpty());
      }

      @Override
      public void clear(Object resource) {
        cleared.add(resource);
      }
    });

    Assert.assertEquals(1, cleared.size());
    Assert.assertEquals(inputFile, cleared.get(0));
  }
}
