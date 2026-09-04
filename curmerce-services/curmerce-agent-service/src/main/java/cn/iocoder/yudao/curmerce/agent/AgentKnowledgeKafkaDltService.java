package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Operator-only view and replay boundary for the Agent knowledge Kafka DLT.
 * The DLT keeps the original record for forensic work, while this service
 * exposes only identity metadata and republishes a selected record to the
 * original topic. Projection checkpoints make replay at-least-once safe.
 */
@Service
@ConditionalOnProperty(prefix = "curmerce.agent.event-projection", name = "enabled", havingValue = "true")
public class AgentKnowledgeKafkaDltService {
    private final AgentKnowledgeProjectionProperties properties;
    private final KafkaTemplate<String, String> template;
    private final ObjectMapper objectMapper;

    public AgentKnowledgeKafkaDltService(AgentKnowledgeProjectionProperties properties,
                                         KafkaTemplate<String, String> template,
                                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.template = template;
        this.objectMapper = objectMapper;
    }

    public List<DeadLetter> list(int partition, long offset, int limit) {
        validate(partition, offset, limit);
        List<ConsumerRecord<String, String>> records = read(partition, offset, limit);
        List<DeadLetter> result = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            result.add(metadata(record));
        }
        return List.copyOf(result);
    }

    /** Republishes exactly one DLT record after validating its tenant scope. */
    public ReplayResult replay(int partition, long offset) {
        validate(partition, offset, 1);
        List<ConsumerRecord<String, String>> records = read(partition, offset, 1);
        ConsumerRecord<String, String> record = records.stream()
                .filter(value -> value.offset() == offset).findFirst().orElse(null);
        if (record == null) return new ReplayResult(false, null, null, "not-found");
        DeadLetter metadata = metadata(record);
        if (!AgentRequestContext.tenantId().equals(AgentRequestContext.normalizeTenant(metadata.tenantId()))) {
            throw new IllegalArgumentException("Kafka 死信租户与当前上下文不匹配");
        }
        try {
            template.send(properties.kafkaTopic(), record.key(), record.value())
                    .get(10, TimeUnit.SECONDS);
            return new ReplayResult(true, record.partition(), record.offset(), "replayed");
        } catch (Exception ex) {
            throw new IllegalStateException("Kafka 知识死信重放失败", ex);
        }
    }

    public String dltTopic() { return properties.kafkaTopic() + ".DLT"; }

    private List<ConsumerRecord<String, String>> read(int partition, long offset, int limit) {
        Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.kafkaBootstrapServers());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "curmerce-agent-dlt-inspector-" + UUID.randomUUID());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            TopicPartition topicPartition = new TopicPartition(dltTopic(), partition);
            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, offset);
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            List<ConsumerRecord<String, String>> result = new ArrayList<>();
            while (System.nanoTime() < deadline && result.size() < limit) {
                consumer.poll(Duration.ofMillis(250)).records(topicPartition).forEach(record -> {
                    if (record.offset() >= offset && result.size() < limit) result.add(record);
                });
                if (!result.isEmpty() && result.size() >= limit) break;
            }
            return List.copyOf(result);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Kafka 知识死信读取失败", ex);
        }
    }

    private DeadLetter metadata(ConsumerRecord<String, String> record) {
        String eventType = "";
        String eventId = "";
        String tenantId = "";
        try {
            JsonNode value = objectMapper.readTree(record.value());
            eventType = value.path("eventType").asText("");
            eventId = value.path("eventId").asText("");
            tenantId = value.path("tenantId").asText("");
        } catch (Exception ignored) {
            eventType = "invalid";
        }
        return new DeadLetter(dltTopic(), record.partition(), record.offset(),
                record.key(), eventType, eventId, tenantId);
    }

    private static void validate(int partition, long offset, int limit) {
        if (partition < 0 || partition > 10_000) throw new IllegalArgumentException("Kafka 分区编号无效");
        if (offset < 0) throw new IllegalArgumentException("Kafka 偏移量无效");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Kafka 死信查询数量必须在 1 到 100 之间");
    }

    public record DeadLetter(String topic, int partition, long offset, String key,
                             String eventType, String eventId, String tenantId) { }
    public record ReplayResult(boolean replayed, Integer partition, Long offset, String status) { }
}
