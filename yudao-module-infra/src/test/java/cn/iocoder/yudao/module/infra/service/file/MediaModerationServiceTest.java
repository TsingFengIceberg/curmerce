package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaModerationServiceTest {

    private final MediaModerationService service = new MediaModerationService();
    @Mock private FileMapper fileMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private MediaMetrics metrics;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(service, "properties", new CurmerceMediaProperties());
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(service, "metrics", metrics);
        ReflectionTestUtils.setField(service, "transactionManager", transactionManager);
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class))).thenReturn(transactionStatus);
    }

    @Test
    void disabledModerationReleasesOriginalAndExistingVariants() {
        FileDO original = new FileDO().setId(1L).setOriginalFileId(null);
        FileDO variant = new FileDO().setId(2L).setOriginalFileId(1L).setAssetStatus(20);
        when(fileMapper.selectById(1L)).thenReturn(original);
        when(fileMapper.selectVariants(1L)).thenReturn(List.of(variant));

        service.onIngested(new MediaAssetIngestedEvent(1L));

        ArgumentCaptor<FileDO> updates = ArgumentCaptor.forClass(FileDO.class);
        verify(fileMapper, times(2)).updateById(updates.capture());
        assertEquals(List.of(10, 10), updates.getAllValues().stream().map(FileDO::getAssetStatus).toList());
        assertEquals(List.of(50, 50), updates.getAllValues().stream().map(FileDO::getModerationStatus).toList());
        verify(eventPublisher).publishEvent(new MediaAssetReadyEvent(1L));
        verify(transactionManager).commit(transactionStatus);
    }
}
