package cn.iocoder.yudao.module.commerce.service.outbox.mq;

import cn.iocoder.yudao.framework.mq.redis.core.RedisMQTemplate;
import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxMessagePublisher;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@ConditionalOnProperty(prefix = "curmerce.outbox", name = "transport", havingValue = "redis", matchIfMissing = true)
public class CommerceOutboxStreamMessagePublisher implements CommerceOutboxMessagePublisher {

    private final RedisMQTemplate redisMQTemplate;

    public void publish(CommerceOutboxEventDO event) {
        CommerceOutboxStreamMessage message = new CommerceOutboxStreamMessage();
        message.setEventId(event.getId());
        message.setEventType(event.getEventType());
        message.setEventKey(event.getEventKey());
        message.setAggregateType(event.getAggregateType());
        message.setAggregateId(event.getAggregateId());
        message.setPayload(event.getPayload());
        redisMQTemplate.send(message);
    }
}
