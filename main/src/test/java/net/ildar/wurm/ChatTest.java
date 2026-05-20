package net.ildar.wurm;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ChatTest {
    @Test
    public void shortMessagesDoNotThrow() {
        Chat.onMessage(":Event", "short", false);
    }

    @Test
    public void processorExceptionsDoNotStopChatProcessing() {
        AtomicInteger calls = new AtomicInteger();
        Chat.MessageProcessor throwingProcessor = Chat.registerMessageProcessor(
                ":Event",
                message -> true,
                () -> { throw new RuntimeException("boom"); });
        Chat.MessageProcessor countingProcessor = Chat.registerMessageProcessor(
                ":Event",
                message -> true,
                calls::incrementAndGet);

        try {
            Chat.onMessage(":Event", "event message", false);
        } finally {
            Chat.unregisterMessageProcessor(throwingProcessor);
            Chat.unregisterMessageProcessor(countingProcessor);
        }

        assertEquals(1, calls.get());
    }
}
