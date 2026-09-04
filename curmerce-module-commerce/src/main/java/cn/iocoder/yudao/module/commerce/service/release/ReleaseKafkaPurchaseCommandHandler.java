package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceOutboxEventTypeEnum;
import cn.iocoder.yudao.module.commerce.service.outbox.kafka.CommerceKafkaEventHandler;
import cn.iocoder.yudao.module.commerce.service.outbox.kafka.CommerceKafkaEventMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Consumes only durable release commands; all other commerce events are ignored. */
@Component
@ConditionalOnProperty(prefix = "curmerce.release", name = "kafka-queue-enabled", havingValue = "true")
public class ReleaseKafkaPurchaseCommandHandler implements CommerceKafkaEventHandler {
    private final ReleaseKafkaPurchaseQueue queue;

    public ReleaseKafkaPurchaseCommandHandler(ReleaseKafkaPurchaseQueue queue) {
        this.queue = queue;
    }

    @Override
    public boolean supports(String eventType) {
        return CommerceOutboxEventTypeEnum.RELEASE_PURCHASE_COMMAND.name().equals(eventType);
    }

    @Override
    public void handle(CommerceKafkaEventMessage message) {
        Map<String, Object> payload = JsonUtils.parseMap(message.getPayload());
        if (payload == null) throw new IllegalArgumentException("限时发售 Kafka 命令缺少负载");
        Object value = payload.get("commandId");
        try {
            long id = value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
            if (id <= 0) throw new NumberFormatException("non-positive command id");
            queue.consume(id);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("限时发售 Kafka 命令编号无效", ex);
        }
    }
}
