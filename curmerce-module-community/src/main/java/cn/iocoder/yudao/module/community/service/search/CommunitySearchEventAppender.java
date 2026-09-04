package cn.iocoder.yudao.module.community.service.search;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.community.dal.dataobject.outbox.CommunitySearchOutboxDO;
import cn.iocoder.yudao.module.community.dal.mysql.outbox.CommunitySearchOutboxMapper;
import cn.iocoder.yudao.module.community.enums.CommunitySearchEventTypeEnum;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

@Component
public class CommunitySearchEventAppender {
    @Resource private CommunitySearchOutboxMapper outboxMapper;

    public void appendState(Long postId, Map<String, Object> payload) {
        if (postId == null) return;
        String tenantId = tenantId();
        String serialized = payload == null ? "{}" : JsonUtils.toJsonString(payload);
        String eventKey = CommunitySearchEventTypeEnum.POST_CHANGED.name() + ":" + postId + ":" + sha256(serialized);
        if (outboxMapper.selectByTypeAndKey(CommunitySearchEventTypeEnum.POST_CHANGED.name(), eventKey) != null) return;
        outboxMapper.insert(new CommunitySearchOutboxDO().setEventType(CommunitySearchEventTypeEnum.POST_CHANGED.name())
                .setTenantId(tenantId)
                .setEventKey(eventKey).setAggregateType("community_post").setAggregateId(postId)
                .setPayload(serialized).setStatus(10).setAttempts(0));
    }

    public static String tenantId() {
        Long value = TenantContextHolder.getTenantId();
        return value == null ? "default" : String.valueOf(value);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
