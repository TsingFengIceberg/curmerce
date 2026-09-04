package cn.iocoder.yudao.module.commerce.service.outbox;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.outbox.CommerceOutboxMapper;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceOutboxEventTypeEnum;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceOutboxStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

/**
 * 事务性 Outbox 事件追加器。
 *
 * <p>必须在业务本地事务内调用：事件行与业务变更一起提交或一起回滚。
 * 每个业务事件使用 (eventType, eventKey) 唯一约束保证幂等，重复追加直接忽略。</p>
 */
@Component
public class CommerceOutboxEventAppender {

    @Resource
    private CommerceOutboxMapper outboxMapper;

    /**
     * 在调用方事务内追加一条 PENDING 事件。
     *
     * @param type        事件类型
     * @param aggregateId 聚合 ID（订单 ID 或退款 ID）
     * @param payload     业务负载，序列化为 JSON 保存
     */
    public void append(CommerceOutboxEventTypeEnum type, Long aggregateId, Map<String, Object> payload) {
        if (type == null || aggregateId == null) {
            return;
        }
        String eventKey = type.name() + ":" + aggregateId;
        if (outboxMapper.selectByTypeAndKey(type.name(), eventKey) != null) {
            return;
        }
        CommerceOutboxEventDO event = new CommerceOutboxEventDO()
                .setTenantId(tenantId())
                .setEventType(type.name())
                .setEventKey(eventKey)
                .setAggregateType(type.getAggregateType())
                .setAggregateId(aggregateId)
                .setPayload(payload == null ? "{}" : JsonUtils.toJsonString(payload))
                .setStatus(CommerceOutboxStatusEnum.PENDING.getStatus())
                .setAttempts(0);
        outboxMapper.insert(event);
    }

    /**
     * Append a deterministic state snapshot. A changed snapshot gets a new
     * event id, while a retried transaction with the same snapshot is ignored.
     * The aggregate id remains the Kafka key so a projection sees one ordered
     * partition per aggregate.
     */
    public void appendState(CommerceOutboxEventTypeEnum type, Long aggregateId, Map<String, Object> payload) {
        if (type == null || aggregateId == null) {
            return;
        }
        String serialized = payload == null ? "{}" : JsonUtils.toJsonString(payload);
        String eventKey = type.name() + ":" + aggregateId + ":" + sha256(serialized);
        if (outboxMapper.selectByTypeAndKey(type.name(), eventKey) != null) {
            return;
        }
        outboxMapper.insert(new CommerceOutboxEventDO()
                .setTenantId(tenantId())
                .setEventType(type.name()).setEventKey(eventKey)
                .setAggregateType(type.getAggregateType()).setAggregateId(aggregateId)
                .setPayload(serialized).setStatus(CommerceOutboxStatusEnum.PENDING.getStatus())
                .setAttempts(0));
    }

    /** Captures the tenant before a scheduled publisher loses request context. */
    public static String tenantId() {
        Long value = TenantContextHolder.getTenantId();
        return value == null ? "default" : String.valueOf(value);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
