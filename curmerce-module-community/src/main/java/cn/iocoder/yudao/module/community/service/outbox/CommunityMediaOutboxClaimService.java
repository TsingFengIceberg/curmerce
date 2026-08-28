package cn.iocoder.yudao.module.community.service.outbox;

import cn.iocoder.yudao.module.community.dal.dataobject.outbox.CommunityMediaOutboxDO;
import cn.iocoder.yudao.module.community.dal.mysql.outbox.CommunityMediaOutboxMapper;
import cn.iocoder.yudao.module.community.enums.CommunityMediaOutboxStatusEnum;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CommunityMediaOutboxClaimService {

    @Resource private CommunityMediaOutboxMapper outboxMapper;
    @Resource private MeterRegistry meterRegistry;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public CommunityMediaOutboxDO claimOne(Duration leaseDuration) {
        CommunityMediaOutboxDO event = outboxMapper.selectClaimableForUpdate();
        if (event == null) {
            return null;
        }
        boolean recovered = event.getStatus() == CommunityMediaOutboxStatusEnum.PROCESSING.getStatus();
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime leaseUntil = LocalDateTime.now().plus(leaseDuration).withNano(0);
        if (outboxMapper.markProcessing(event.getId(), event.getVersion(), token, leaseUntil) != 1) {
            return null;
        }
        if (recovered) {
            meterRegistry.counter("curmerce.community.media.outbox.lease.recovered").increment();
        }
        return event.setStatus(CommunityMediaOutboxStatusEnum.PROCESSING.getStatus())
                .setProcessingToken(token).setLeaseUntil(leaseUntil);
    }
}
