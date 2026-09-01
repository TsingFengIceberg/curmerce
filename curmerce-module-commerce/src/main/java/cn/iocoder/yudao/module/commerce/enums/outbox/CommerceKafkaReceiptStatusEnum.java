package cn.iocoder.yudao.module.commerce.enums.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Durable processing states for a Kafka consumer group. */
@Getter
@AllArgsConstructor
public enum CommerceKafkaReceiptStatusEnum {
    PROCESSING(10), PROCESSED(20), FAILED(30), REQUEUED(40);
    private final int status;
}
