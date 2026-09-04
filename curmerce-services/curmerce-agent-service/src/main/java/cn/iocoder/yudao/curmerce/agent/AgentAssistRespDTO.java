package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class AgentAssistRespDTO {
    private String query;
    private String summary;
    private JsonNode products;
    private JsonNode communityPosts;
    private List<String> degradedSources;
    private boolean modelBacked;
    private String modelAnswer;
    private AgentUsageRecorder.Usage usage;
    private List<SpringAiCompatibleChatClient.ToolCall> toolCalls;
    private List<SpringAiCompatibleChatClient.ToolResult> toolResults;
    private List<String> groundingWarnings;
    /** Stable, bounded references used to verify an answer in the UI or audit log. */
    private List<AgentSourceReference> references;

    public record AgentSourceReference(String source, String id, String title,
                                       String excerpt, String path) { }
}
