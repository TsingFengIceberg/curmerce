package cn.iocoder.yudao.module.commerce.service.outbox.kafka;

import lombok.Data;

/** Wire envelope for a commerce Outbox event on Kafka. */
@Data
public class CommerceKafkaEventMessage {
    private Long eventId;
    private String eventType;
    private String eventKey;
    private String aggregateType;
    private Long aggregateId;
    private String payload;
}
