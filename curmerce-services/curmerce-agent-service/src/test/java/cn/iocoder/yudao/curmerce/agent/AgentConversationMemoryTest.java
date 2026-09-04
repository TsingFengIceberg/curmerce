package cn.iocoder.yudao.curmerce.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentConversationMemoryTest {
    @Test
    void memoryIsBoundedToRecentMessages() {
        AgentConversationMemory memory = new AgentConversationMemory();
        for (int i = 0; i < 20; i++) memory.append("c1", "user", "m" + i);
        assertEquals(12, memory.history("c1").size());
        assertEquals("m8", memory.history("c1").get(0).content());
    }
}
