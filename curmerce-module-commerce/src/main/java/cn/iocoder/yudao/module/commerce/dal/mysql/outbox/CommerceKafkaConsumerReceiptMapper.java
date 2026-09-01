package cn.iocoder.yudao.module.commerce.dal.mysql.outbox;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceKafkaConsumerReceiptDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceKafkaReceiptStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Mapper
public interface CommerceKafkaConsumerReceiptMapper extends BaseMapperX<CommerceKafkaConsumerReceiptDO> {

    default boolean exists(String consumerGroup, Long eventId) {
        return selectCount(new LambdaQueryWrapper<CommerceKafkaConsumerReceiptDO>()
                .eq(CommerceKafkaConsumerReceiptDO::getConsumerGroup, consumerGroup)
                .eq(CommerceKafkaConsumerReceiptDO::getEventId, eventId)) > 0;
    }

    default CommerceKafkaConsumerReceiptDO selectByGroupAndEvent(String consumerGroup, Long eventId) {
        return selectOne(new LambdaQueryWrapper<CommerceKafkaConsumerReceiptDO>()
                .eq(CommerceKafkaConsumerReceiptDO::getConsumerGroup, consumerGroup)
                .eq(CommerceKafkaConsumerReceiptDO::getEventId, eventId));
    }

    default int markProcessing(Long id, int attempts) {
        return update(new CommerceKafkaConsumerReceiptDO().setStatus(CommerceKafkaReceiptStatusEnum.PROCESSING.getStatus())
                        .setAttempts(attempts).setLastError(null).setProcessedTime(null),
                new LambdaUpdateWrapper<CommerceKafkaConsumerReceiptDO>().eq(CommerceKafkaConsumerReceiptDO::getId, id));
    }

    default int markProcessed(Long id, LocalDateTime time) {
        return update(new CommerceKafkaConsumerReceiptDO().setStatus(CommerceKafkaReceiptStatusEnum.PROCESSED.getStatus())
                        .setProcessedTime(time).setLastError(null),
                new LambdaUpdateWrapper<CommerceKafkaConsumerReceiptDO>().eq(CommerceKafkaConsumerReceiptDO::getId, id));
    }

    default int markFailed(Long id, int attempts, String error) {
        return update(new CommerceKafkaConsumerReceiptDO().setStatus(CommerceKafkaReceiptStatusEnum.FAILED.getStatus())
                        .setAttempts(attempts).setLastError(error),
                new LambdaUpdateWrapper<CommerceKafkaConsumerReceiptDO>().eq(CommerceKafkaConsumerReceiptDO::getId, id));
    }

    default int markRequeued(Long id) {
        return update(new CommerceKafkaConsumerReceiptDO().setStatus(CommerceKafkaReceiptStatusEnum.REQUEUED.getStatus())
                        .setLastError(null),
                new LambdaUpdateWrapper<CommerceKafkaConsumerReceiptDO>().eq(CommerceKafkaConsumerReceiptDO::getId, id)
                        .eq(CommerceKafkaConsumerReceiptDO::getStatus, CommerceKafkaReceiptStatusEnum.FAILED.getStatus()));
    }

    default java.util.List<CommerceKafkaConsumerReceiptDO> selectFailed(int limit) {
        int safe = Math.max(1, Math.min(limit, 500));
        return selectList(new LambdaQueryWrapper<CommerceKafkaConsumerReceiptDO>()
                .eq(CommerceKafkaConsumerReceiptDO::getStatus, CommerceKafkaReceiptStatusEnum.FAILED.getStatus())
                .orderByAsc(CommerceKafkaConsumerReceiptDO::getId).last("LIMIT " + safe));
    }

    default Map<Integer, Long> countByStatus() {
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (CommerceKafkaReceiptStatusEnum status : CommerceKafkaReceiptStatusEnum.values()) {
            result.put(status.getStatus(), selectCount(new LambdaQueryWrapper<CommerceKafkaConsumerReceiptDO>()
                    .eq(CommerceKafkaConsumerReceiptDO::getStatus, status.getStatus())));
        }
        return result;
    }
}
