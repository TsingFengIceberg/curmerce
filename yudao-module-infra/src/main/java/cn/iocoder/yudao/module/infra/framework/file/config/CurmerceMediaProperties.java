package cn.iocoder.yudao.module.infra.framework.file.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "curmerce.media")
@Validated
@Data
public class CurmerceMediaProperties {

    private DataSize maxUploadSize = DataSize.ofMegabytes(10);

    @Min(1)
    @Max(20_000)
    private int maxWidth = 10_000;

    @Min(1)
    @Max(20_000)
    private int maxHeight = 10_000;

    @Min(1)
    private long maxPixels = 40_000_000L;

    @NotEmpty
    private Set<String> allowedMimeTypes = new LinkedHashSet<>(Set.of(
            "image/jpeg", "image/png", "image/webp"));

    private Duration unboundRetention = Duration.ofHours(24);

    private Duration replacedRetention = Duration.ofDays(7);

    private Duration accessTimestampInterval = Duration.ofHours(1);

    private boolean derivativesEnabled = true;

    private Duration cleanupDelay = Duration.ofHours(1);

    private Duration uploadTicketTtl = Duration.ofMinutes(10);

    private Duration uploadTicketProcessingLease = Duration.ofMinutes(15);

    private Quota memberQuota = new Quota(100, DataSize.ofMegabytes(200), DataSize.ofGigabytes(2));

    private Quota adminQuota = new Quota(500, DataSize.ofGigabytes(1), DataSize.ofGigabytes(10));

    private ClamAv clamAv = new ClamAv();

    private Moderation moderation = new Moderation();

    private Imgproxy imgproxy = new Imgproxy();

    @Data
    public static class ClamAv {
        private boolean enabled;
        private boolean failClosed = true;
        private String host = "127.0.0.1";
        @Min(1)
        @Max(65_535)
        private int port = 3310;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(15);
    }

    @Data
    public static class Moderation {
        private boolean enabled;
        private String endpoint;
        private String bearerToken;
        private boolean failClosed = true;
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration requestTimeout = Duration.ofSeconds(20);
    }

    @Data
    public static class Imgproxy {
        private boolean enabled;
        private String endpoint = "http://127.0.0.1:58081";
        private String keyHex;
        private String saltHex;
        private Duration requestTimeout = Duration.ofSeconds(30);
        @Min(30)
        @Max(3600)
        private int sourceUrlTtlSeconds = 300;
    }

    @Data
    public static class Quota {
        @Min(1)
        private int dailyUploadCount;
        private DataSize dailyUploadBytes;
        private DataSize totalStoredBytes;

        public Quota() {
        }

        public Quota(int dailyUploadCount, DataSize dailyUploadBytes, DataSize totalStoredBytes) {
            this.dailyUploadCount = dailyUploadCount;
            this.dailyUploadBytes = dailyUploadBytes;
            this.totalStoredBytes = totalStoredBytes;
        }
    }
}
