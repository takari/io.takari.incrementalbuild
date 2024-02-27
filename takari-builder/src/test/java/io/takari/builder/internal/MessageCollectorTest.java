package io.takari.builder.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.takari.builder.internal.Message.MessageSeverity;
import io.takari.builder.testing.BuilderExecutionException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.slf4j.helpers.NOPLogger;

public class MessageCollectorTest {

    MessageCollector testee = new MessageCollector(NOPLogger.NOP_LOGGER);

    @Test
    public void testThrowExceptionIfThereWereErrorMessages_success() throws Exception {
        testee.info(new File(""), 0, 0, "info", null);
        testee.warn(new File(""), 0, 0, "warn", null);

        testee.throwExceptionIfThereWereErrorMessages(BuilderExecutionException::new);
    }

    @Test(expected = BuilderExecutionException.class)
    public void testThrowExceptionIfThereWereErrorMessages_error() throws Exception {
        testee.error(new File(""), 0, 0, "error", null);
        testee.throwExceptionIfThereWereErrorMessages(BuilderExecutionException::new);
    }

    @Test(expected = BuilderExecutionException.class)
    public void testMessageReplay_error() throws Exception {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message(new File("").toString(), 0, 0, "error", MessageSeverity.ERROR, null));
        testee.replayMessages(BuilderExecutionException::new, messages);
    }

    @Test
    public void testConcurrentModification() throws Exception {
        // this test asserts that message collector tolerates incorrect builders
        // that continue to log messages after main builder method finished execution
        final int messageCount = 10;
        final int fileCount = 1000;
        AtomicInteger count = new AtomicInteger();
        Runnable worker = () -> {
            int no;
            do {
                no = count.incrementAndGet();
                File file = new File(Integer.toString(no));
                for (int i = 0; i < messageCount; i++) {
                    testee.info(file, i, 0, "message", null);
                }
            } while (no < fileCount);
        };

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            Thread thread = new Thread(worker);
            threads.add(thread);
            thread.start();
        }

        while (count.get() < fileCount) {
            testee.throwExceptionIfThereWereErrorMessages(Exception::new);
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(testee.getCollectedMessages()).hasSize(count.get() * messageCount);
    }
}
