package cn.iocoder.yudao.curmerce.auction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rebuilds the optional Redis bid gate from the Auction-owned database. */
@Component
@ConditionalOnProperty(prefix = "curmerce.auction", name = "local-store-enabled", havingValue = "true")
public class AuctionOwnedBidGateReconciliationJob {
    private final AuctionOwnedRepository repository;
    private final AuctionOwnedBidGate gate;
    private final AuctionServiceProperties properties;

    public AuctionOwnedBidGateReconciliationJob(AuctionOwnedRepository repository, AuctionOwnedBidGate gate,
                                                AuctionServiceProperties properties) {
        this.repository = repository;
        this.gate = gate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${curmerce.auction.redis-reconcile-delay-ms:60000}")
    public void reconcile() {
        if (!properties.redisGateEnabled()) return;
        for (Long id : repository.activeSessionIds(500)) {
            AuctionOwnedRepository.AuctionBidRow highest = repository.highestBid(id);
            gate.reconcile(id, highest == null ? null : highest.amount(), highest == null ? null : highest.bidderUserId());
        }
    }
}
