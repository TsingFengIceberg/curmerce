package cn.iocoder.yudao.module.commerce.service.outbox.mq;

import cn.iocoder.yudao.framework.mq.redis.core.stream.AbstractRedisStreamMessageListener;
import cn.iocoder.yudao.module.commerce.dal.mysql.outbox.CommerceOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Outbox 事件的幂等消费者。
 *
 * <p>当前第一版中业务效果已经在本地事务里随事件行一起提交，因此消费者只做
 * 投递确认：若事件在发布后、标记前崩溃而残留 PENDING，则由消费者补标 PUBLISHED，
 * 保证发布语义最终一致。后续异步投影（如通知、搜索索引）在这个监听器里扩展。</p>
 */
@Slf4j
@Component
public class CommerceOutboxStreamMessageListener
        extends AbstractRedisStreamMessageListener<CommerceOutboxStreamMessage> {

    private final CommerceOutboxMapper outboxMapper;

    public CommerceOutboxStreamMessageListener(CommerceOutboxMapper outboxMapper) {
        this.outboxMapper = outboxMapper;
    }

    @Override
    public void onMessage(CommerceOutboxStreamMessage message) {
        if (message.getEventId() == null) {
            return;
        }
        outboxMapper.markPublished(message.getEventId(), LocalDateTime.now().withNano(0));
        log.info("[onMessage][收到并确认 Outbox 事件 {}:{}，聚合 {}({})]",
                message.getEventType(), message.getEventKey(),
                message.getAggregateType(), message.getAggregateId());
    }
}
