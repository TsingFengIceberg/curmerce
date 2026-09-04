package cn.iocoder.yudao.curmerce.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Kafka settings for the optional product/post knowledge projection. */
@ConfigurationProperties(prefix = "curmerce.agent.event-projection")
public record AgentKnowledgeProjectionProperties(boolean enabled, String kafkaBootstrapServers,
                                                 String kafkaTopic, String kafkaConsumerGroup,
                                                 long retryBackoffMs, long maxRetries, long lockSeconds) {
    public AgentKnowledgeProjectionProperties {
        kafkaBootstrapServers = value(kafkaBootstrapServers, "127.0.0.1:19092");
        kafkaTopic = value(kafkaTopic, "curmerce.agent.events.v1");
        kafkaConsumerGroup = value(kafkaConsumerGroup, "curmerce-agent-knowledge-v1");
        retryBackoffMs = Math.max(100L, Math.min(retryBackoffMs <= 0 ? 1_000L : retryBackoffMs, 60_000L));
        maxRetries = Math.max(0L, Math.min(maxRetries, 100L));
        lockSeconds = Math.max(10L, Math.min(lockSeconds <= 0 ? 120L : lockSeconds, 900L));
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
