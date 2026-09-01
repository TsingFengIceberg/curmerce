package cn.iocoder.yudao.module.commerce.service.auction;

import cn.iocoder.yudao.module.commerce.dal.dataobject.auction.CommerceAuctionBidDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.auction.CommerceAuctionBidMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.auction.CommerceAuctionSessionMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Rebuilds optional auction hot-key state from MySQL after Redis loss. */
@Component
public class AuctionBidGateReconciliationJob {
    @Resource private AuctionBidConcurrencyGate gate;
    @Resource private CommerceAuctionSessionMapper sessionMapper;
    @Resource private CommerceAuctionBidMapper bidMapper;
    @Value("${curmerce.auction.redis-gate-enabled:false}") private boolean enabled;

    @Scheduled(fixedDelayString = "${curmerce.auction.redis-reconcile-delay-ms:60000}")
    public void reconcile() {
        if (!enabled) return;
        for (var session : sessionMapper.selectActiveForReconciliation(500)) {
            CommerceAuctionBidDO highest = bidMapper.selectHighest(session.getId());
            gate.reconcile(session.getId(), highest == null ? null : highest.getAmount(), highest == null ? null : highest.getBidderUserId());
        }
    }
}
