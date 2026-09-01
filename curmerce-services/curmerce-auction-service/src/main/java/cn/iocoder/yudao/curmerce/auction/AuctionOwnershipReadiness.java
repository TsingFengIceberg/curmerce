package cn.iocoder.yudao.curmerce.auction;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Prevents the Auction service from becoming a writer before the ownership
 * cutover has been verified. The flag can be disabled only for an explicitly
 * controlled migration or recovery run.
 */
@Component
@ConditionalOnProperty(prefix = "curmerce.auction", name = "local-store-enabled", havingValue = "true")
public class AuctionOwnershipReadiness {
    private final AuctionOwnershipReconciliationService reconciliation;
    private final AuctionServiceProperties properties;

    public AuctionOwnershipReadiness(AuctionOwnershipReconciliationService reconciliation,
                                     AuctionServiceProperties properties) {
        this.reconciliation = reconciliation;
        this.properties = properties;
    }

    @PostConstruct
    public void verifyBeforeWrites() {
        if (!properties.requireCutoverVerification()) return;
        Map<String, Object> report = reconciliation.verify();
        if (!Boolean.TRUE.equals(report.get("verified"))) {
            throw new IllegalStateException("Auction local store is enabled but ownership cutover verification failed; "
                    + "apply migration 28 and run auction-cutover-smoke.sh before starting writers");
        }
    }
}
