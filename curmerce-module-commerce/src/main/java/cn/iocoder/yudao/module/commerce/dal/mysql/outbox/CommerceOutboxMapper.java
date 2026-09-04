package cn.iocoder.yudao.module.commerce.dal.mysql.outbox;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceOutboxStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxEventAppender;

@Mapper
public interface CommerceOutboxMapper extends BaseMapperX<CommerceOutboxEventDO> {

    default CommerceOutboxEventDO selectByTypeAndKey(String eventType, String eventKey) {
        return selectOne(new LambdaQueryWrapper<CommerceOutboxEventDO>()
                .eq(CommerceOutboxEventDO::getEventType, eventType)
                .eq(CommerceOutboxEventDO::getEventKey, eventKey)
                .eq(CommerceOutboxEventDO::getTenantId, CommerceOutboxEventAppender.tenantId()));
    }

    /**
     * 领取一批到期待发布的 PENDING 事件，使用 SKIP LOCKED 避免多个发布器重复领取。
     * next_retry_time 为空表示首次发布，立即领取。
     */
    default List<CommerceOutboxEventDO> selectPendingForUpdate(int batchSize) {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 1000));
        LocalDateTime now = LocalDateTime.now();
        return selectList(new LambdaQueryWrapper<CommerceOutboxEventDO>()
                .eq(CommerceOutboxEventDO::getStatus, CommerceOutboxStatusEnum.PENDING.getStatus())
                .and(w -> w.isNull(CommerceOutboxEventDO::getNextRetryTime)
                        .or().le(CommerceOutboxEventDO::getNextRetryTime, now))
                .orderByAsc(CommerceOutboxEventDO::getId)
                .last("LIMIT " + safeBatchSize + " FOR UPDATE SKIP LOCKED"));
    }

    default int markPublished(Long id, LocalDateTime publishedTime) {
        return update(new CommerceOutboxEventDO().setStatus(CommerceOutboxStatusEnum.PUBLISHED.getStatus())
                        .setPublishedTime(publishedTime),
                new LambdaUpdateWrapper<CommerceOutboxEventDO>().eq(CommerceOutboxEventDO::getId, id)
                        .eq(CommerceOutboxEventDO::getStatus, CommerceOutboxStatusEnum.PENDING.getStatus()));
    }

    default int markRetry(Long id, int attempts, LocalDateTime nextRetryTime, String lastError) {
        return update(new CommerceOutboxEventDO().setAttempts(attempts).setNextRetryTime(nextRetryTime)
                        .setLastError(lastError),
                new LambdaUpdateWrapper<CommerceOutboxEventDO>().eq(CommerceOutboxEventDO::getId, id)
                        .eq(CommerceOutboxEventDO::getStatus, CommerceOutboxStatusEnum.PENDING.getStatus()));
    }

    default int markDead(Long id, String lastError) {
        return update(new CommerceOutboxEventDO().setStatus(CommerceOutboxStatusEnum.DEAD.getStatus())
                        .setLastError(lastError),
                new LambdaUpdateWrapper<CommerceOutboxEventDO>().eq(CommerceOutboxEventDO::getId, id)
                        .eq(CommerceOutboxEventDO::getStatus, CommerceOutboxStatusEnum.PENDING.getStatus()));
    }

    default int requeueDead(Long id) {
        return update(new CommerceOutboxEventDO().setStatus(CommerceOutboxStatusEnum.PENDING.getStatus())
                        .setAttempts(0).setNextRetryTime(null).setLastError(null).setPublishedTime(null),
                new LambdaUpdateWrapper<CommerceOutboxEventDO>().eq(CommerceOutboxEventDO::getId, id)
                        .eq(CommerceOutboxEventDO::getStatus, CommerceOutboxStatusEnum.DEAD.getStatus()));
    }

    default List<CommerceOutboxEventDO> selectDead(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return selectList(new LambdaQueryWrapper<CommerceOutboxEventDO>()
                .eq(CommerceOutboxEventDO::getStatus, CommerceOutboxStatusEnum.DEAD.getStatus())
                .orderByAsc(CommerceOutboxEventDO::getId)
                .last("LIMIT " + safeLimit));
    }

    default long countByStatus(Integer status) {
        return selectCount(new LambdaQueryWrapper<CommerceOutboxEventDO>()
                .eq(CommerceOutboxEventDO::getStatus, status));
    }
}
