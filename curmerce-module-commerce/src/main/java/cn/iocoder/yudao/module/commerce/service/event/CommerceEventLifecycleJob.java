package cn.iocoder.yudao.module.commerce.service.event;

import cn.iocoder.yudao.module.commerce.service.auction.AuctionService;
import cn.iocoder.yudao.module.commerce.service.release.ReleaseService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Advances basic release and auction states without introducing a distributed scheduler. */
@Component
public class CommerceEventLifecycleJob {
    @Resource private ReleaseService releaseService;
    @Resource private AuctionService auctionService;

    @Scheduled(fixedDelayString = "${curmerce.commerce-event.lifecycle-delay-ms:30000}")
    public void advance() {
        LocalDateTime now = LocalDateTime.now();
        releaseService.advanceStatuses(now);
        auctionService.advanceStatuses(now, 100);
    }
}
