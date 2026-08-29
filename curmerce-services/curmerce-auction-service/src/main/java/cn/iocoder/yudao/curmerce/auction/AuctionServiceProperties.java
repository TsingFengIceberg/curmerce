package cn.iocoder.yudao.curmerce.auction;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "curmerce.auction")
public record AuctionServiceProperties(String coreBaseUrl, Duration connectTimeout, Duration readTimeout) {
    public AuctionServiceProperties {
        coreBaseUrl = normalize(coreBaseUrl, "http://127.0.0.1:48080");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.replaceAll("/$", "");
    }
}
