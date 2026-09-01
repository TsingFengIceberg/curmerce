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
}
