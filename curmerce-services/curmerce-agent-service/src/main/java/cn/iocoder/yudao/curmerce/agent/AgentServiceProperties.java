package cn.iocoder.yudao.curmerce.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "curmerce.agent")
public record AgentServiceProperties(String coreBaseUrl, String communityBaseUrl,
                                     Duration connectTimeout, Duration readTimeout) {
    public AgentServiceProperties {
        coreBaseUrl = coreBaseUrl == null ? "http://127.0.0.1:48080" : coreBaseUrl;
        communityBaseUrl = communityBaseUrl == null ? "http://127.0.0.1:48083" : communityBaseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(2) : readTimeout;
    }
}
