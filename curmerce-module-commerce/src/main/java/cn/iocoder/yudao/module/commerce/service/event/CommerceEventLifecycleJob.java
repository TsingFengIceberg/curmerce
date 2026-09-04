package cn.iocoder.yudao.module.commerce.service.event;

import cn.iocoder.yudao.module.commerce.service.auction.AuctionService;
import cn.iocoder.yudao.module.commerce.service.release.ReleaseService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Advances basic release and auction states without introducing a distributed scheduler. */
@Component
public class CommerceEventLifecycleJob {
    @Resource private ReleaseService releaseService;
    @Resource private AuctionService auctionService;
    private final StringRedisTemplate redis;
    private final long leaseSeconds;
    private static final DefaultRedisScript<Long> RELEASE_LEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end
            """, Long.class);

    public CommerceEventLifecycleJob(ObjectProvider<StringRedisTemplate> provider,
                                     @Value("${curmerce.commerce-event.lifecycle-lease-seconds:25}") long leaseSeconds) {
        this.redis = provider.getIfAvailable();
        this.leaseSeconds = Math.max(5, leaseSeconds);
    }

    @Scheduled(fixedDelayString = "${curmerce.commerce-event.lifecycle-delay-ms:30000}")
    public void advance() {
        String lease = UUID.randomUUID().toString();
        if (redis != null) {
            try {
                Boolean acquired = redis.opsForValue().setIfAbsent("curmerce:commerce:lifecycle:lease", lease,
                        Duration.ofSeconds(leaseSeconds));
                if (!Boolean.TRUE.equals(acquired)) return;
            } catch (RuntimeException ignored) { return; }
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            releaseService.advanceStatuses(now);
            auctionService.advanceStatuses(now, 100);
        } finally {
            if (redis != null) {
                try { redis.execute(RELEASE_LEASE, List.of("curmerce:commerce:lifecycle:lease"), lease); }
                catch (RuntimeException ignored) { }
            }
        }
    }
}
