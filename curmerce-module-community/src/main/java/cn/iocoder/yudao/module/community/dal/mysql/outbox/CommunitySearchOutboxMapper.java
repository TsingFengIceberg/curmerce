package cn.iocoder.yudao.module.community.dal.mysql.outbox;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.community.dal.dataobject.outbox.CommunitySearchOutboxDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;
import cn.iocoder.yudao.module.community.service.search.CommunitySearchEventAppender;

@Mapper
public interface CommunitySearchOutboxMapper extends BaseMapperX<CommunitySearchOutboxDO> {

    default CommunitySearchOutboxDO selectByTypeAndKey(String eventType, String eventKey) {
        return selectOne(new LambdaQueryWrapper<CommunitySearchOutboxDO>()
                .eq(CommunitySearchOutboxDO::getEventType, eventType)
                .eq(CommunitySearchOutboxDO::getEventKey, eventKey)
                .eq(CommunitySearchOutboxDO::getTenantId, CommunitySearchEventAppender.tenantId()));
    }

    default List<CommunitySearchOutboxDO> selectPending(int batchSize) {
        int safeSize = Math.max(1, Math.min(batchSize, 200));
        return selectList(new LambdaQueryWrapper<CommunitySearchOutboxDO>()
                .eq(CommunitySearchOutboxDO::getStatus, 10)
                .and(w -> w.isNull(CommunitySearchOutboxDO::getNextRetryTime)
                        .or().le(CommunitySearchOutboxDO::getNextRetryTime, LocalDateTime.now()))
                .orderByAsc(CommunitySearchOutboxDO::getId).last("LIMIT " + safeSize + " FOR UPDATE SKIP LOCKED"));
    }

    default int markPublished(Long id, LocalDateTime time) {
        return update(new CommunitySearchOutboxDO().setStatus(30).setPublishedTime(time).setLastError(null),
                new LambdaUpdateWrapper<CommunitySearchOutboxDO>().eq(CommunitySearchOutboxDO::getId, id)
                        .eq(CommunitySearchOutboxDO::getStatus, 10));
    }

    default int markRetry(Long id, int attempts, LocalDateTime nextRetryTime, String lastError) {
        return update(new CommunitySearchOutboxDO().setAttempts(attempts).setNextRetryTime(nextRetryTime)
                        .setLastError(lastError),
                new LambdaUpdateWrapper<CommunitySearchOutboxDO>().eq(CommunitySearchOutboxDO::getId, id)
                        .eq(CommunitySearchOutboxDO::getStatus, 10));
    }

    default int markDead(Long id, int attempts, String lastError) {
        return update(new CommunitySearchOutboxDO().setStatus(20).setAttempts(attempts)
                        .setNextRetryTime(null).setLastError(lastError),
                new LambdaUpdateWrapper<CommunitySearchOutboxDO>().eq(CommunitySearchOutboxDO::getId, id)
                        .eq(CommunitySearchOutboxDO::getStatus, 10));
    }

    default List<CommunitySearchOutboxDO> selectDead(int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        return selectList(new LambdaQueryWrapper<CommunitySearchOutboxDO>()
                .eq(CommunitySearchOutboxDO::getStatus, 20).orderByAsc(CommunitySearchOutboxDO::getId)
                .last("LIMIT " + safe));
    }

    default int requeueDead(Long id) {
        return update(new CommunitySearchOutboxDO().setStatus(10).setAttempts(0)
                        .setNextRetryTime(null).setLastError(null).setPublishedTime(null),
                new LambdaUpdateWrapper<CommunitySearchOutboxDO>().eq(CommunitySearchOutboxDO::getId, id)
                        .eq(CommunitySearchOutboxDO::getStatus, 20));
    }

    default java.util.Map<Integer, Long> countByStatus() {
        java.util.Map<Integer, Long> result = new java.util.LinkedHashMap<>();
        for (Integer status : java.util.List.of(10, 20, 30)) {
            result.put(status, selectCount(new LambdaQueryWrapper<CommunitySearchOutboxDO>()
                    .eq(CommunitySearchOutboxDO::getStatus, status)));
        }
        return result;
    }
}
