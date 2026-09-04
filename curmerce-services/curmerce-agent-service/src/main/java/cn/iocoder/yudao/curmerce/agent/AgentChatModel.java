package cn.iocoder.yudao.curmerce.agent;

/** Stable model boundary equivalent to Spring AI's ChatModel contract. */
public interface AgentChatModel {
    boolean enabled();
    SpringAiCompatibleChatClient.ModelAnswer complete(String query, String context);

    default SpringAiCompatibleChatClient.ModelAnswer completeWithToolResults(
            String query, String context, SpringAiCompatibleChatClient.ModelAnswer previous,
            java.util.List<SpringAiCompatibleChatClient.ToolResult> results) {
        return previous;
    }
}
