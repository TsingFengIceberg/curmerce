package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseItemDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseItemMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps Redis reservations aligned with committed MySQL stock when no request
 * is in-flight for an item. Active reservations are deliberately left alone.
 */
@Slf4j
@Component
public class ReleaseReservationReconciliationJob {
    @Resource private CommerceReleaseItemMapper itemMapper;
    @Resource private ReleaseReservationService reservationService;

    @Scheduled(fixedDelayString = "${curmerce.release.reservation-reconcile-delay-ms:60000}")
    public void reconcile() {
        int skipped = 0;
        for (CommerceReleaseItemDO item : itemMapper.selectForReservationReconciliation(500)) {
            if (!reservationService.reconcileStock(item.getCampaignId(), item.getId(), item.getStock())) {
                skipped++;
            }
        }
        if (skipped > 0) {
            log.info("[releaseReservationReconcile] skipped {} items with active reservations or unavailable Redis", skipped);
        }
    }
}
