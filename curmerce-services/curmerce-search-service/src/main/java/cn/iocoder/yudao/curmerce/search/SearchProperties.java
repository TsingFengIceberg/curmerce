package cn.iocoder.yudao.curmerce.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "curmerce.search")
public record SearchProperties(
        boolean enabled,
        String elasticsearchUrl,
        String productIndex,
        String postIndex,
        String kafkaBootstrapServers,
        String kafkaTopic,
        String kafkaConsumerGroup,
        String coreBaseUrl,
        String communityBaseUrl,
        Duration requestTimeout,
        String rebuildToken) {
    public SearchProperties {
        elasticsearchUrl = normalize(elasticsearchUrl, "http://127.0.0.1:19200");
        productIndex = normalize(productIndex, "curmerce-products-v1");
        postIndex = normalize(postIndex, "curmerce-posts-v1");
        kafkaBootstrapServers = normalize(kafkaBootstrapServers, "127.0.0.1:19092");
        kafkaTopic = normalize(kafkaTopic, "curmerce.events.v1");
        kafkaConsumerGroup = normalize(kafkaConsumerGroup, "curmerce-search-v1");
        coreBaseUrl = normalize(coreBaseUrl, "http://127.0.0.1:48080");
        communityBaseUrl = normalize(communityBaseUrl, "http://127.0.0.1:48083");
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
        rebuildToken = rebuildToken == null ? "" : rebuildToken;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.replaceAll("/$", "");
    }
}
