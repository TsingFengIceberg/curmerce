package cn.iocoder.yudao.curmerce.agent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFeedbackRecorderTest {

    @Test
    void recordsOnlyHashedIdentifiersAndAcceptsAnUpdatedReaction() {
        AgentFeedbackRecorder recorder = new AgentFeedbackRecorder(new SimpleMeterRegistry());

        AgentFeedbackRecorder.Feedback first = recorder.record(42L, "conversation-1", "message-1", true, "sources");
        AgentFeedbackRecorder.Feedback changed = recorder.record(42L, "conversation-1", "message-1", false, "sources");

        assertNotEquals("conversation-1", first.conversationHash());
        assertNotEquals("message-1", first.messageHash());
        assertEquals(first.messageHash(), changed.messageHash());
        assertFalse(changed.helpful());
    }

    @Test
    void rejectsUnboundedIdsAndUnknownCategories() {
        AgentFeedbackRecorder recorder = new AgentFeedbackRecorder(new SimpleMeterRegistry());

        assertThrows(IllegalArgumentException.class, () -> recorder.record(1L, " ", "message", true, "answer"));
        assertThrows(IllegalArgumentException.class, () -> recorder.record(1L, "conversation", "message", true, "other"));
    }
}
