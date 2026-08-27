package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.media.MediaMigrationReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.media.MediaMigrationRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.MediaMigrationDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.MediaMigrationMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Import(MediaMigrationService.class)
class MediaMigrationServiceTest extends BaseDbUnitTest {

    @Resource private MediaMigrationService service;
    @Resource private FileMapper fileMapper;
    @Resource private MediaMigrationMapper migrationMapper;
    @MockitoBean private FileConfigService fileConfigService;

    private FileClient source;
    private FileClient target;

    @BeforeEach
    void setUp() {
        source = mock(FileClient.class);
        target = mock(FileClient.class);
        when(target.getId()).thenReturn(2L);
        when(fileConfigService.getFileClient(1L)).thenReturn(source);
        when(fileConfigService.getFileClient(2L)).thenReturn(target);
    }

    @Test
    void dryRunReportsCandidatesWithoutWritingObjectsOrAuditRows() throws Exception {
        insertReadyFile(new byte[]{1, 2, 3});

        MediaMigrationRespVO result = service.migrate(request(true, false));

        assertTrue(result.dryRun());
        assertEquals(1, result.candidates());
        assertEquals(3L, result.candidateBytes());
        assertEquals(0L, migrationMapper.selectCount());
        verifyNoInteractions(source);
        verify(target, never()).upload(any(), anyString(), anyString());
    }

    @Test
    void verifiedCopySwitchesMetadataWithoutDeletingSource() throws Exception {
        byte[] content = new byte[]{4, 5, 6};
        FileDO file = insertReadyFile(content);
        when(source.getContent(file.getPath())).thenReturn(content);
        when(target.upload(same(content), anyString(), eq("image/png"))).thenReturn("target-url");
        when(target.getContent(anyString())).thenReturn(content);
        when(target.presignGetUrl(anyString(), isNull())).thenReturn("signed-target-url");

        MediaMigrationRespVO result = service.migrate(request(false, true));

        assertFalse(result.dryRun());
        assertEquals(1, result.switched());
        FileDO switched = fileMapper.selectById(file.getId());
        assertEquals(2L, switched.getConfigId());
        assertTrue(switched.getPath().startsWith("migrated/"));
        assertEquals("signed-target-url", switched.getUrl());
        MediaMigrationDO audit = migrationMapper.selectOne(MediaMigrationDO::getFileId, file.getId());
        assertEquals(20, audit.getStatus());
        assertEquals(DigestUtil.sha256Hex(content), audit.getSha256());
        assertNotNull(audit.getCopiedAt());
        assertNotNull(audit.getSwitchedAt());
        verify(source, never()).delete(anyString());
    }

    @Test
    void failedTargetVerificationIsAuditedAndLeavesSourceMetadataUntouched() throws Exception {
        byte[] content = new byte[]{7, 8, 9};
        FileDO file = insertReadyFile(content);
        when(source.getContent(file.getPath())).thenReturn(content);
        when(target.upload(same(content), anyString(), eq("image/png"))).thenReturn("target-url");
        when(target.getContent(anyString())).thenReturn(new byte[]{9, 8, 7});

        MediaMigrationRespVO result = service.migrate(request(false, true));

        assertEquals(1, result.failed());
        assertEquals(1L, fileMapper.selectById(file.getId()).getConfigId());
        MediaMigrationDO audit = migrationMapper.selectOne(MediaMigrationDO::getFileId, file.getId());
        assertEquals(30, audit.getStatus());
        assertTrue(audit.getLastError().contains("SHA-256"));
        verify(target).delete(audit.getTargetPath());
        verify(source, never()).delete(anyString());
    }

    @Test
    void pendingAuditIsClaimedAndResumed() throws Exception {
        byte[] content = new byte[]{10, 11, 12};
        FileDO file = insertReadyFile(content);
        MediaMigrationDO audit = insertAudit(file, 0, null);
        when(source.getContent(file.getPath())).thenReturn(content);
        when(target.getContent(audit.getTargetPath())).thenReturn(content);

        MediaMigrationRespVO result = service.migrate(request(false, false));

        assertEquals(1, result.copied());
        MediaMigrationDO resumed = migrationMapper.selectById(audit.getId());
        assertEquals(10, resumed.getStatus());
        assertEquals(1, resumed.getAttemptCount());
        assertEquals(DigestUtil.sha256Hex(content), resumed.getSha256());
    }

    @Test
    void freshProcessingAuditIsNotClaimedByAnotherRun() throws Exception {
        FileDO file = insertReadyFile(new byte[]{13, 14, 15});
        insertAudit(file, 5, null);

        MediaMigrationRespVO result = service.migrate(request(false, false));

        assertEquals(0, result.candidates());
        verifyNoInteractions(source);
        verify(target, never()).upload(any(), anyString(), anyString());
    }

    private FileDO insertReadyFile(byte[] content) {
        String assetKey = java.util.UUID.randomUUID().toString();
        FileDO file = new FileDO().setAssetKey(assetKey).setConfigId(1L).setName("test.png")
                .setPath("legacy/" + assetKey + ".png").setUrl("legacy-url").setType("image/png")
                .setSize((long) content.length).setSha256(DigestUtil.sha256Hex(content))
                .setAssetStatus(10).setScanStatus(10).setModerationStatus(50).setVisibility(0)
                .setOwnerUserId(7L).setOwnerUserType(1).setBoundOnce(true);
        fileMapper.insert(file);
        return file;
    }

    private MediaMigrationDO insertAudit(FileDO file, int status, String sha256) {
        MediaMigrationDO audit = new MediaMigrationDO().setFileId(file.getId()).setSourceConfigId(1L)
                .setTargetConfigId(2L).setSourcePath(file.getPath())
                .setTargetPath("migrated/" + file.getAssetKey() + ".png")
                .setSha256(sha256).setStatus(status).setAttemptCount(0);
        migrationMapper.insert(audit);
        return audit;
    }

    private static MediaMigrationReqVO request(boolean dryRun, boolean switchMetadata) {
        return new MediaMigrationReqVO().setTargetConfigId(2L).setBatchSize(50)
                .setDryRun(dryRun).setSwitchMetadata(switchMetadata);
    }
}
