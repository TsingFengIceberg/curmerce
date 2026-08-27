package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_ASSET_STATE_INVALID;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaManagementServiceTest {

    private final MediaManagementService service = new MediaManagementService();
    @Mock private FileMapper fileMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private MediaMetrics metrics;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(service, "metrics", metrics);
    }

    @Test
    void releaseRejectsAssetsThatAreNotQuarantined() {
        when(fileMapper.selectById(1L)).thenReturn(new FileDO().setId(1L).setAssetStatus(10));

        assertServiceException(() -> service.release(1L, null), FILE_ASSET_STATE_INVALID);

        verify(fileMapper, never()).updateById(any(FileDO.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void releaseRestoresOriginalAndVariantsAndStartsDerivativeCheck() {
        FileDO original = new FileDO().setId(1L).setAssetStatus(20);
        when(fileMapper.selectById(1L)).thenReturn(original);
        when(fileMapper.selectVariants(1L)).thenReturn(List.of(new FileDO().setId(2L).setOriginalFileId(1L)));

        service.release(1L, "reviewed");

        verify(fileMapper, times(2)).updateById(argThat((FileDO update) -> Integer.valueOf(10).equals(update.getAssetStatus())
                && Integer.valueOf(10).equals(update.getModerationStatus())
                && "reviewed".equals(update.getModerationReason())));
        verify(eventPublisher).publishEvent(new MediaAssetReadyEvent(1L));
    }
}
