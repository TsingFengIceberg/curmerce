package cn.iocoder.yudao.curmerce.auction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verifies that the Auction-owned store still contains exactly the snapshot
 * recorded by the cutover migration.  The Auction account deliberately has
 * no access to Core's schema, so the source counts are the immutable values
 * written by the stopped-writer migration; this check also detects local
 * truncation or an incomplete copy without weakening schema ownership.
 */
@Service
@ConditionalOnProperty(prefix = "curmerce.auction", name = "local-store-enabled", havingValue = "true")
public class AuctionOwnershipReconciliationService {
    private final AuctionOwnedRepository repository;

    public AuctionOwnershipReconciliationService(AuctionOwnedRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> verify() {
        AuctionOwnedRepository.OwnershipCounts counts = repository.ownershipCounts();
        AuctionOwnedRepository.CutoverSnapshot cutover = counts.cutover();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("verified", counts.verified());
        report.put("sessionCount", counts.sessionCount());
        report.put("bidCount", counts.bidCount());
        report.put("cutover", cutover);
        report.put("writeModel", "auction-owned");
        report.put("coreTables", "read-only-compatibility-source");
        return report;
    }
}
