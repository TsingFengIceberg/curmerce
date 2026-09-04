package cn.iocoder.yudao.module.commerce.dal.mysql.release;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleaseReservationDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import java.util.List;

@Mapper
public interface CommerceReleaseReservationMapper extends BaseMapperX<CommerceReleaseReservationDO> {
    default CommerceReleaseReservationDO selectByIdentity(Long campaignId, Long itemId, Long buyerUserId,
                                                             String reservationKey) {
        return selectOne(new LambdaQueryWrapper<CommerceReleaseReservationDO>()
                .eq(CommerceReleaseReservationDO::getTenantId, tenantId())
                .eq(CommerceReleaseReservationDO::getCampaignId, campaignId)
                .eq(CommerceReleaseReservationDO::getItemId, itemId)
                .eq(CommerceReleaseReservationDO::getBuyerUserId, buyerUserId)
                .eq(CommerceReleaseReservationDO::getReservationKey, reservationKey));
    }

    default List<CommerceReleaseReservationDO> selectPendingFinalization(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return selectList(new LambdaQueryWrapper<CommerceReleaseReservationDO>()
                .eq(CommerceReleaseReservationDO::getTenantId, tenantId())
                .eq(CommerceReleaseReservationDO::getStatus, CommerceReleaseReservationDO.COMMITTED)
                .apply("(next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP)")
                .orderByAsc(CommerceReleaseReservationDO::getId)
                .last("LIMIT " + safeLimit));
    }

    default int markFinalized(Long id) {
        return update(new CommerceReleaseReservationDO().setStatus(CommerceReleaseReservationDO.FINALIZED)
                        .setLastError(null).setNextRetryAt(null),
                new LambdaUpdateWrapper<CommerceReleaseReservationDO>()
                        .eq(CommerceReleaseReservationDO::getId, id)
                        .eq(CommerceReleaseReservationDO::getTenantId, tenantId())
                        .eq(CommerceReleaseReservationDO::getStatus, CommerceReleaseReservationDO.COMMITTED));
    }

    default int markError(Long id, String error) {
        String safe = error == null ? "Redis reservation finalization unavailable" : error;
        return update(new CommerceReleaseReservationDO().setLastError(safe.substring(0, Math.min(500, safe.length()))),
                new LambdaUpdateWrapper<CommerceReleaseReservationDO>()
                        .setSql("attempts = attempts + 1, "
                                + "status = IF(attempts + 1 >= 8, " + CommerceReleaseReservationDO.DEAD + ", " + CommerceReleaseReservationDO.COMMITTED + "), "
                                + "next_retry_at = IF(attempts + 1 >= 8, NULL, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL "
                                + "(30 * LEAST(64, POW(2, GREATEST(0, attempts)))) SECOND))")
                        .eq(CommerceReleaseReservationDO::getId, id)
                        .eq(CommerceReleaseReservationDO::getTenantId, tenantId())
                        .eq(CommerceReleaseReservationDO::getStatus, CommerceReleaseReservationDO.COMMITTED));
    }

    /** Reopens a dead reservation only after an operator has inspected Redis and SQL state. */
    default int replayDeadFinalization(Long id) {
        return update(new CommerceReleaseReservationDO().setStatus(CommerceReleaseReservationDO.COMMITTED)
                        .setAttempts(0).setNextRetryAt(null).setLastError(null),
                new LambdaUpdateWrapper<CommerceReleaseReservationDO>()
                        .eq(CommerceReleaseReservationDO::getId, id)
                        .eq(CommerceReleaseReservationDO::getTenantId, tenantId())
                        .eq(CommerceReleaseReservationDO::getStatus, CommerceReleaseReservationDO.DEAD));
    }

    default int markErrorByIdentity(Long campaignId, Long itemId, Long buyerUserId,
                                    String reservationKey, String error) {
        String safe = error == null ? "Redis reservation finalization unavailable" : error;
        return update(new CommerceReleaseReservationDO().setLastError(safe.substring(0, Math.min(500, safe.length()))),
                new LambdaUpdateWrapper<CommerceReleaseReservationDO>()
                        .setSql("attempts = attempts + 1, "
                                + "status = IF(attempts + 1 >= 8, " + CommerceReleaseReservationDO.DEAD + ", " + CommerceReleaseReservationDO.COMMITTED + "), "
                                + "next_retry_at = IF(attempts + 1 >= 8, NULL, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL "
                                + "(30 * LEAST(64, POW(2, GREATEST(0, attempts)))) SECOND))")
                        .eq(CommerceReleaseReservationDO::getCampaignId, campaignId)
                        .eq(CommerceReleaseReservationDO::getTenantId, tenantId())
                        .eq(CommerceReleaseReservationDO::getItemId, itemId)
                        .eq(CommerceReleaseReservationDO::getBuyerUserId, buyerUserId)
                        .eq(CommerceReleaseReservationDO::getReservationKey, reservationKey)
                        .eq(CommerceReleaseReservationDO::getStatus, CommerceReleaseReservationDO.COMMITTED));
    }

    default String tenantId() {
        Long value = TenantContextHolder.getTenantId();
        return value == null ? "default" : String.valueOf(value);
    }
}
