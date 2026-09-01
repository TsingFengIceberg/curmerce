package cn.iocoder.yudao.module.commerce.service.outbox.kafka;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Dispatches events to owning-module projections without cross-module table access. */
@Component
public class CommerceKafkaEventDispatcher {
    private final ObjectProvider<CommerceKafkaEventHandler> handlers;

    public CommerceKafkaEventDispatcher(ObjectProvider<CommerceKafkaEventHandler> handlers) {
        this.handlers = handlers;
    }

    public int dispatch(CommerceKafkaEventMessage message) {
        int handled = 0;
        for (CommerceKafkaEventHandler handler : handlers) {
            if (handler.supports(message.getEventType())) {
                handler.handle(message);
                handled++;
            }
        }
        return handled;
    }
}
