package cn.iocoder.yudao.module.community.service.outbox;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.community.dal.dataobject.outbox.CommunityMediaOutboxDO;
import cn.iocoder.yudao.module.community.dal.mysql.outbox.CommunityMediaOutboxMapper;
import cn.iocoder.yudao.module.community.service.integration.CommunityMediaClient;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@Slf4j
public class CommunityMediaOutboxPublisher {

    @Resource private CommunityMediaOutboxClaimService claimService;
    @Resource private CommunityMediaOutboxMapper outboxMapper;
    @Resource private CommunityMediaClient mediaClient;
    @Resource private MeterRegistry meterRegistry;

    @Value("${curmerce.community-media-outbox.batch-size:50}") private int batchSize;
    @Value("${curmerce.community-media-outbox.lease-duration:30s}") private Duration leaseDuration;
    @Value("${curmerce.community-media-outbox.max-backoff:5m}") private Duration maxBackoff;

    @PostConstruct
    void registerMetrics() {
        Gauge.builder("curmerce.community.media.outbox.unfinished", outboxMapper,
                        CommunityMediaOutboxMapper::countUnfinished)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${curmerce.community-media-outbox.publish-delay-ms:2000}")
    public void publishAvailable() {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
        for (int i = 0; i < safeBatchSize; i++) {
            CommunityMediaOutboxDO event = claimService.claimOne(leaseDuration);
            if (event == null) {
                return;
            }
            publish(event);
        }
    }

    void publish(CommunityMediaOutboxDO event) {
        try {
            mediaClient.replaceFileReferences(event.getBusinessType(), event.getBusinessId(), event.getFieldName(),
                    JsonUtils.parseArray(event.getPayload(), String.class));
            if (outboxMapper.markSucceeded(event.getId(), event.getVersion(), event.getProcessingToken(),
                    LocalDateTime.now().withNano(0)) == 1) {
                meterRegistry.counter("curmerce.community.media.outbox.published", "result", "success").increment();
                return;
            }
            outboxMapper.releaseNewerVersion(event.getId(), event.getVersion(), event.getProcessingToken());
            meterRegistry.counter("curmerce.community.media.outbox.published", "result", "superseded").increment();
        } catch (RuntimeException ex) {
            int attempts = event.getAttempts() + 1;
            long backoffSeconds = Math.min(maxBackoff.toSeconds(), 1L << Math.min(attempts, 20));
            int changed = outboxMapper.markRetry(event.getId(), event.getVersion(), event.getProcessingToken(), attempts,
                    LocalDateTime.now().plusSeconds(Math.max(1, backoffSeconds)).withNano(0), truncate(ex.getMessage()));
            if (changed == 0) {
                outboxMapper.releaseNewerVersion(event.getId(), event.getVersion(), event.getProcessingToken());
            }
            meterRegistry.counter("curmerce.community.media.outbox.published", "result", "retry").increment();
            log.warn("community media reference sync failed: outboxId={}, version={}, attempts={}, reason={}",
                    event.getId(), event.getVersion(), attempts, ex.getMessage());
        }
    }

    private static String truncate(String message) {
        String safe = message == null ? "unknown failure" : message;
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }
}
