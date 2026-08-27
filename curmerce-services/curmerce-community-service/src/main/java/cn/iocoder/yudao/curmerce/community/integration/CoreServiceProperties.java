package cn.iocoder.yudao.curmerce.community.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "curmerce.cloud.core")
public record CoreServiceProperties(String baseUrl, String internalToken, Duration connectTimeout,
                                    Duration readTimeout) {
    public CoreServiceProperties {
        baseUrl = baseUrl == null ? "http://127.0.0.1:48080" : baseUrl;
        internalToken = internalToken == null ? "" : internalToken;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(2) : readTimeout;
    }
}
