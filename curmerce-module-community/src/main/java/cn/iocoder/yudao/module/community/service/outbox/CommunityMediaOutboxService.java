package cn.iocoder.yudao.module.community.service.outbox;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.community.dal.mysql.outbox.CommunityMediaOutboxMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class CommunityMediaOutboxService {

    @Resource private CommunityMediaOutboxMapper outboxMapper;
    @Resource private MeterRegistry meterRegistry;

    public void recordDesiredState(String businessType, String businessId, String fieldName,
                                   Collection<String> urls) {
        List<String> normalized = urls == null ? List.of() : List.copyOf(new LinkedHashSet<>(urls));
        outboxMapper.upsertDesiredState(businessType, businessId, fieldName,
                JsonUtils.toJsonString(normalized));
        meterRegistry.counter("curmerce.community.media.outbox.recorded").increment();
    }
}
