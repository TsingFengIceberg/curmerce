package cn.iocoder.yudao.module.community.service.outbox;

import cn.iocoder.yudao.module.community.dal.dataobject.outbox.CommunityMediaOutboxDO;
import cn.iocoder.yudao.module.community.dal.mysql.outbox.CommunityMediaOutboxMapper;
import cn.iocoder.yudao.module.community.enums.CommunityMediaOutboxStatusEnum;
import cn.iocoder.yudao.module.community.service.integration.CommunityMediaClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityMediaOutboxServiceTest {

    @Mock private CommunityMediaOutboxMapper outboxMapper;
    @Mock private CommunityMediaClient mediaClient;
    private SimpleMeterRegistry meterRegistry;
    private CommunityMediaOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        publisher = new CommunityMediaOutboxPublisher();
        ReflectionTestUtils.setField(publisher, "outboxMapper", outboxMapper);
        ReflectionTestUtils.setField(publisher, "mediaClient", mediaClient);
        ReflectionTestUtils.setField(publisher, "meterRegistry", meterRegistry);
        ReflectionTestUtils.setField(publisher, "maxBackoff", Duration.ofMinutes(5));
    }

    @Test
    void recordDesiredStateDeduplicatesUrlsBeforeTransactionalUpsert() {
        CommunityMediaOutboxService service = new CommunityMediaOutboxService();
        ReflectionTestUtils.setField(service, "outboxMapper", outboxMapper);
        ReflectionTestUtils.setField(service, "meterRegistry", meterRegistry);

        service.recordDesiredState("community_post", "10", "media", List.of("/a.jpg", "/a.jpg", "/b.jpg"));

        verify(outboxMapper).upsertDesiredState("community_post", "10", "media", "[\"/a.jpg\",\"/b.jpg\"]");
        assertEquals(1.0, meterRegistry.get("curmerce.community.media.outbox.recorded").counter().count());
    }

    @Test
    void staleWorkerCannotMarkNewerDesiredStateSucceeded() {
        CommunityMediaOutboxDO event = event().setVersion(3L);
        when(outboxMapper.markSucceeded(eq(1L), eq(3L), eq("lease-token"), any())).thenReturn(0);

        publisher.publish(event);

        verify(mediaClient).replaceFileReferences("community_post", "10", "media", List.of("/new.jpg"));
        verify(outboxMapper).releaseNewerVersion(1L, 3L, "lease-token");
        assertEquals(1.0, meterRegistry.get("curmerce.community.media.outbox.published")
                .tag("result", "superseded").counter().count());
    }

    @Test
    void failedDeliveryReturnsSameVersionToPendingWithBackoff() {
        CommunityMediaOutboxDO event = event().setAttempts(2);
        doThrow(new IllegalStateException("core unavailable")).when(mediaClient)
                .replaceFileReferences(any(), any(), any(), any());
        when(outboxMapper.markRetry(eq(1L), eq(1L), eq("lease-token"), eq(3), any(),
                eq("core unavailable"))).thenReturn(1);

        publisher.publish(event);

        verify(outboxMapper).markRetry(eq(1L), eq(1L), eq("lease-token"), eq(3), any(),
                eq("core unavailable"));
        assertEquals(1.0, meterRegistry.get("curmerce.community.media.outbox.published")
                .tag("result", "retry").counter().count());
    }

    @Test
    void expiredProcessingLeaseCanBeClaimedAgain() {
        CommunityMediaOutboxClaimService service = new CommunityMediaOutboxClaimService();
        ReflectionTestUtils.setField(service, "outboxMapper", outboxMapper);
        ReflectionTestUtils.setField(service, "meterRegistry", meterRegistry);
        CommunityMediaOutboxDO expired = event().setStatus(CommunityMediaOutboxStatusEnum.PROCESSING.getStatus());
        when(outboxMapper.selectClaimableForUpdate()).thenReturn(expired);
        when(outboxMapper.markProcessing(eq(1L), eq(1L), any(), any())).thenReturn(1);

        CommunityMediaOutboxDO claimed = service.claimOne(Duration.ofSeconds(30));

        assertEquals(CommunityMediaOutboxStatusEnum.PROCESSING.getStatus(), claimed.getStatus());
        assertEquals(1.0, meterRegistry.get("curmerce.community.media.outbox.lease.recovered").counter().count());
    }

    private static CommunityMediaOutboxDO event() {
        return new CommunityMediaOutboxDO().setId(1L).setBusinessType("community_post")
                .setBusinessId("10").setFieldName("media").setPayload("[\"/new.jpg\"]")
                .setVersion(1L).setStatus(CommunityMediaOutboxStatusEnum.PROCESSING.getStatus())
                .setAttempts(0).setProcessingToken("lease-token");
    }
}
