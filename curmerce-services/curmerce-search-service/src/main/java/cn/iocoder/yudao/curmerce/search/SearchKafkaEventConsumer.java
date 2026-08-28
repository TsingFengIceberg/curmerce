package cn.iocoder.yudao.curmerce.search;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "curmerce.search", name = "enabled", havingValue = "true")
public class SearchKafkaEventConsumer {
    private final SearchProjectionService projectionService;
    private final MeterRegistry meterRegistry;

    public SearchKafkaEventConsumer(SearchProjectionService projectionService, MeterRegistry meterRegistry) {
        this.projectionService = projectionService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = "${curmerce.search.kafka-topic:curmerce.events.v1}",
            groupId = "${curmerce.search.kafka-consumer-group:curmerce-search-v1}",
            containerFactory = "searchKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Map<String, Object> envelope = JsonUtils.parseMap(record.value());
        projectionService.project(envelope);
        acknowledgment.acknowledge();
        meterRegistry.counter("curmerce.search.kafka.events", "result", "accepted").increment();
    }
}
