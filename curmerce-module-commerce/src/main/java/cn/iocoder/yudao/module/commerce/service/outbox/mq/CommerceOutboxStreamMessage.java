package cn.iocoder.yudao.module.commerce.service.outbox.mq;

import cn.iocoder.yudao.framework.mq.redis.core.stream.AbstractRedisStreamMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Outbox 事件发布到 Redis Stream 的消息载体。
 * 业务效果已由本地事务随事件行一起提交，消费者按需做幂等投影或通知。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceOutboxStreamMessage extends AbstractRedisStreamMessage {

    private Long eventId;
    private String eventType;
    private String eventKey;
    private String aggregateType;
    private Long aggregateId;
    private String payload;
}
