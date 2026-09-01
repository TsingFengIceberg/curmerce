package cn.iocoder.yudao.curmerce.auction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(prefix = "curmerce.auction", name = "local-store-enabled", havingValue = "true")
public class AuctionOwnedLifecycleJob {
    private final AuctionOwnedService service;

    public AuctionOwnedLifecycleJob(AuctionOwnedService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${curmerce.auction.lifecycle-delay-ms:10000}")
    public void advance() { service.advanceStatuses(LocalDateTime.now(), 100); }
}
