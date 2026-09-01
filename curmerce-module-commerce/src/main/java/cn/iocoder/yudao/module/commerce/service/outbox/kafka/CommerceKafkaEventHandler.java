package cn.iocoder.yudao.module.commerce.service.outbox.kafka;

/** Typed extension point for idempotent domain projections of commerce events. */
public interface CommerceKafkaEventHandler {
    boolean supports(String eventType);
    void handle(CommerceKafkaEventMessage message);
}
