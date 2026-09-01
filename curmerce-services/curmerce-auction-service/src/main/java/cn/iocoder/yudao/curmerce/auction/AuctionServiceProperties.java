package cn.iocoder.yudao.curmerce.auction;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "curmerce.auction")
public record AuctionServiceProperties(String coreBaseUrl, Duration connectTimeout, Duration readTimeout,
                                       String coreInternalToken, boolean localStoreEnabled,
                                       boolean redisGateEnabled, long redisGateTtlSeconds) {
    public AuctionServiceProperties(String coreBaseUrl, Duration connectTimeout, Duration readTimeout) {
        this(coreBaseUrl, connectTimeout, readTimeout, "", false, false, 86400);
    }

    public AuctionServiceProperties {
        coreBaseUrl = normalize(coreBaseUrl, "http://127.0.0.1:48080");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        coreInternalToken = coreInternalToken == null ? "" : coreInternalToken.trim();
        redisGateTtlSeconds = Math.max(60, Math.min(redisGateTtlSeconds, 7 * 24 * 60 * 60));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.replaceAll("/$", "");
    }
}
