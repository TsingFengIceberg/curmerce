package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseItemDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseItemMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleaseReservationMapper;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;

/**
 * Keeps Redis reservations aligned with committed MySQL stock when no request
 * is in-flight for an item. Active reservations are deliberately left alone.
 */
@Slf4j
@Component
public class ReleaseReservationReconciliationJob {
    @Resource private CommerceReleaseItemMapper itemMapper;
    @Resource private ReleaseReservationService reservationService;
    @Resource private CommerceReleaseReservationMapper reservationMapper;
    @Value("${curmerce.release.reservation-orphan-grace-ms:120000}")
    private long orphanGraceMs = 120_000L;

    @Scheduled(fixedDelayString = "${curmerce.release.reservation-reconcile-delay-ms:60000}")
    public void reconcile() {
        int skipped = 0;
        java.util.Set<String> scopes = reservationService.tenantScopes();
        if (scopes.isEmpty()) scopes = java.util.Set.of("default");
        for (String scope : scopes) {
            final int[] scopeSkipped = {0};
            Runnable work = () -> {
                finalizeCommittedReservations(scope);
                releaseOrphanReservations(scope);
                for (CommerceReleaseItemDO item : itemMapper.selectForReservationReconciliation(500)) {
                    if (!reservationService.reconcileStock(scope, item.getCampaignId(), item.getId(), item.getStock())) {
                        scopeSkipped[0]++;
                    }
                }
            };
            if ("default".equals(scope)) work.run();
            else TenantUtils.execute(parseTenant(scope), work);
            skipped += scopeSkipped[0];
        }
        if (skipped > 0) {
            log.info("[releaseReservationReconcile] skipped {} items with active reservations or unavailable Redis", skipped);
        }
    }

    private static Long parseTenant(String scope) {
        if (scope == null || scope.isBlank() || "default".equals(scope)) return 0L;
        try { return Long.valueOf(scope); } catch (NumberFormatException ex) { return 0L; }
    }

    private void releaseOrphanReservations(String scope) {
        long cutoff = Instant.now().toEpochMilli() - Math.max(10_000L, orphanGraceMs);
        for (ReleaseReservationService.ActiveReservation active : reservationService.activeReservations(scope, 200)) {
            if (active.createdAtEpochMs() > cutoff) continue;
            try {
                if (reservationMapper.selectByIdentity(active.campaignId(), active.itemId(), active.userId(),
                        active.reservationKey()) != null) continue;
            } catch (RuntimeException ex) {
                continue;
            }
            reservationService.releaseTracked(scope, active.campaignId(), active.itemId(), active.userId(),
                    active.quantity(), active.reservationKey());
        }
    }

    /** Completes Redis finalization after a process died between SQL commit and afterCommit. */
    private void finalizeCommittedReservations(String scope) {
        try {
            for (CommerceReleaseReservationDO row : reservationMapper.selectPendingFinalization(200)) {
                boolean finalized = reservationService.commitTracked(scope, row.getCampaignId(), row.getItemId(),
                        row.getBuyerUserId(), row.getQuantity(), row.getReservationKey());
                if (!finalized) {
                    CommerceReleaseItemDO item = itemMapper.selectById(row.getItemId());
                    if (item != null && item.getStock() != null) {
                        finalized = reservationService.recoverCommitted(scope, row.getCampaignId(), row.getItemId(),
                                row.getBuyerUserId(), row.getQuantity(), row.getReservationKey(), item.getStock());
                    }
                }
                if (finalized) reservationMapper.markFinalized(row.getId());
                else reservationMapper.markError(row.getId(), "Redis reservation finalization deferred to next reconciliation");
            }
        } catch (RuntimeException ex) {
            // Migration 37 is intentionally deployable after the application;
            // a missing table must not stop the older stock reconciliation.
            log.debug("[releaseReservationReconcile] committed reservation ledger unavailable", ex);
        }
    }

    /** Releases Redis reservations left by a rolled-back/crashed transaction when no ledger row exists. */
}
