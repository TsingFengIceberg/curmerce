package cn.iocoder.yudao.module.commerce.service.outbox.kafka;

import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceKafkaConsumerReceiptDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.outbox.CommerceKafkaConsumerReceiptMapper;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceKafkaReceiptStatusEnum;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "curmerce.outbox", name = "transport", havingValue = "kafka")
public class CommerceKafkaReceiptService {
    private final CommerceKafkaConsumerReceiptMapper mapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final String group;
    private final MeterRegistry meterRegistry;
    private final Duration processingLease;

    public CommerceKafkaReceiptService(CommerceKafkaConsumerReceiptMapper mapper,
                                       KafkaTemplate<String, String> kafkaTemplate,
                                       @Value("${curmerce.outbox.kafka.topic:curmerce.events.v1}") String topic,
                                       @Value("${curmerce.outbox.kafka.consumer-group:curmerce-core-ledger-v1}") String group,
                                       MeterRegistry meterRegistry,
                                       @Value("${curmerce.outbox.kafka.processing-lease:10m}") Duration processingLease) {
        this.mapper = mapper;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.group = group;
        this.meterRegistry = meterRegistry;
        this.processingLease = processingLease == null || processingLease.isNegative() || processingLease.isZero()
                ? Duration.ofMinutes(10) : processingLease;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public BeginResult begin(CommerceKafkaEventMessage message) {
        CommerceKafkaConsumerReceiptDO existing = mapper.selectByGroupAndEvent(group, message.getEventId());
        if (existing != null) {
            if (CommerceKafkaReceiptStatusEnum.PROCESSED.getStatus() == existing.getStatus()) {
                return new BeginResult(existing, false);
            }
            int attempts = (existing.getAttempts() == null ? 0 : existing.getAttempts()) + 1;
            LocalDateTime now = LocalDateTime.now().withNano(0);
            int claimed = mapper.claimProcessing(existing.getId(), attempts, now,
                    now.minus(processingLease));
            if (claimed != 1) return new BeginResult(existing, false);
            return new BeginResult(existing.setAttempts(attempts).setStatus(CommerceKafkaReceiptStatusEnum.PROCESSING.getStatus())
                    .setProcessingTime(now), true);
        }
        try {
            CommerceKafkaConsumerReceiptDO receipt = new CommerceKafkaConsumerReceiptDO()
                    .setConsumerGroup(group).setEventId(message.getEventId()).setEventType(message.getEventType())
                    .setEventKey(message.getEventKey()).setPayload(message.getPayload())
                    .setStatus(CommerceKafkaReceiptStatusEnum.PROCESSING.getStatus()).setAttempts(1)
                    .setProcessingTime(LocalDateTime.now().withNano(0))
                    .setReceivedTime(LocalDateTime.now().withNano(0));
            mapper.insert(receipt);
            return new BeginResult(receipt, true);
        } catch (DuplicateKeyException ex) {
            CommerceKafkaConsumerReceiptDO duplicate = mapper.selectByGroupAndEvent(group, message.getEventId());
            if (duplicate == null) throw ex;
            return new BeginResult(duplicate, false);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void processed(Long receiptId) {
        mapper.markProcessed(receiptId, LocalDateTime.now().withNano(0));
        meterRegistry.counter("curmerce.kafka.consumer.events", "result", "processed").increment();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void failed(Long receiptId, int attempts, Throwable error) {
        String message = error == null ? "unknown failure" : (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        mapper.markFailed(receiptId, attempts, message.length() > 500 ? message.substring(0, 500) : message);
        meterRegistry.counter("curmerce.kafka.consumer.events", "result", "failed").increment();
    }

    @Transactional(readOnly = true)
    public Map<Integer, Long> statusCounts() { return mapper.countByStatus(); }

    public int replayFailed(int limit) {
        int replayed = 0;
        for (CommerceKafkaConsumerReceiptDO receipt : mapper.selectFailed(limit)) {
            if (receipt.getPayload() == null || receipt.getPayload().isBlank()) continue;
            try {
                kafkaTemplate.send(topic, receipt.getEventKey(), receipt.getPayload())
                        .get(5, java.util.concurrent.TimeUnit.SECONDS);
                if (mapper.markRequeued(receipt.getId()) == 1) replayed++;
            } catch (Exception ex) {
                meterRegistry.counter("curmerce.kafka.consumer.replay", "result", "failed").increment();
            }
        }
        return replayed;
    }

    public record BeginResult(CommerceKafkaConsumerReceiptDO receipt, boolean claimed) { }
}
