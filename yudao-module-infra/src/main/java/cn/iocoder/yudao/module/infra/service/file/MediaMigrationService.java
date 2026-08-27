package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.media.MediaMigrationReqVO;
import cn.iocoder.yudao.module.infra.controller.admin.file.vo.media.MediaMigrationRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.MediaMigrationDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.MediaMigrationMapper;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_NOT_EXISTS;

@Service
@Slf4j
public class MediaMigrationService {

    private static final int PROCESSING = 5;
    private static final int COPIED = 10;
    private static final int SWITCHED = 20;
    private static final int FAILED = 30;
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(15);

    @Resource private FileMapper fileMapper;
    @Resource private MediaMigrationMapper migrationMapper;
    @Resource private FileConfigService fileConfigService;
    @Resource private PlatformTransactionManager transactionManager;

    public MediaMigrationRespVO migrate(MediaMigrationReqVO request) {
        FileClient target = fileConfigService.getFileClient(request.getTargetConfigId());
        if (target == null) throw exception(FILE_CONFIG_NOT_EXISTS);
        int limit = request.getBatchSize() == null ? 50 : request.getBatchSize();
        boolean switchMetadata = Boolean.TRUE.equals(request.getSwitchMetadata());
        LocalDateTime staleBefore = LocalDateTimeUtil.now().minus(PROCESSING_LEASE);
        List<FileDO> candidates = migrationMapper.selectCandidates(request.getTargetConfigId(),
                switchMetadata, staleBefore, limit);
        long bytes = candidates.stream().map(FileDO::getSize).filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue).sum();
        if (!Boolean.FALSE.equals(request.getDryRun())) {
            return new MediaMigrationRespVO(true, candidates.size(), bytes, 0, 0, 0, 0);
        }
        int copied = 0;
        int switched = 0;
        int skipped = 0;
        int failed = 0;
        for (FileDO file : candidates) {
            MigrationOutcome outcome = migrateOne(file, target, switchMetadata, staleBefore);
            copied += outcome.copied ? 1 : 0;
            switched += outcome.switched ? 1 : 0;
            skipped += outcome.skipped ? 1 : 0;
            failed += outcome.failed ? 1 : 0;
        }
        return new MediaMigrationRespVO(false, candidates.size(), bytes, copied, switched, skipped, failed);
    }

    private MigrationOutcome migrateOne(FileDO file, FileClient target, boolean switchMetadata,
                                        LocalDateTime staleBefore) {
        MediaMigrationDO audit = migrationMapper.selectByFileAndTarget(file.getId(), target.getId());
        if (audit != null && Integer.valueOf(SWITCHED).equals(audit.getStatus())) return MigrationOutcome.asSkipped();
        boolean claimedByInsert = false;
        if (audit == null) {
            audit = new MediaMigrationDO().setFileId(file.getId()).setSourceConfigId(file.getConfigId())
                    .setTargetConfigId(target.getId()).setSourcePath(file.getPath())
                    .setTargetPath(targetPath(file)).setStatus(PROCESSING).setAttemptCount(1);
            try {
                migrationMapper.insert(audit);
                claimedByInsert = true;
            } catch (DuplicateKeyException ignored) {
                audit = migrationMapper.selectByFileAndTarget(file.getId(), target.getId());
                if (audit == null) throw ignored;
            }
        }
        boolean resumeCopied = Integer.valueOf(COPIED).equals(audit.getStatus());
        if (!claimedByInsert && migrationMapper.claim(audit.getId(), switchMetadata, staleBefore) == 0) {
            return MigrationOutcome.asSkipped();
        }
        if (!claimedByInsert) audit = migrationMapper.selectById(audit.getId());
        try {
            String expectedHash = audit.getSha256();
            if (!resumeCopied) {
                FileClient source = fileConfigService.getFileClient(audit.getSourceConfigId());
                if (source == null) throw new IllegalStateException("Source file client no longer exists");
                byte[] sourceContent = source.getContent(audit.getSourcePath());
                expectedHash = DigestUtil.sha256Hex(sourceContent);
                if (StrUtil.isNotBlank(file.getSha256()) && !file.getSha256().equals(expectedHash)) {
                    throw new IllegalStateException("Source SHA-256 differs from asset metadata");
                }
                target.upload(sourceContent, audit.getTargetPath(), file.getType());
                byte[] targetContent = target.getContent(audit.getTargetPath());
                String actualHash = DigestUtil.sha256Hex(targetContent);
                if (!expectedHash.equals(actualHash)) {
                    try { target.delete(audit.getTargetPath()); } catch (Exception ignored) { }
                    throw new IllegalStateException("Target SHA-256 verification failed");
                }
                audit.setSha256(expectedHash).setStatus(COPIED).setCopiedAt(LocalDateTimeUtil.now())
                        .setLastError(null);
                migrationMapper.updateById(audit);
            } else {
                byte[] targetContent = target.getContent(audit.getTargetPath());
                if (!audit.getSha256().equals(DigestUtil.sha256Hex(targetContent))) {
                    throw new IllegalStateException("Previously copied target no longer matches audit hash");
                }
            }
            if (!switchMetadata) return MigrationOutcome.asCopied();
            String url = target.presignGetUrl(audit.getTargetPath(), null);
            MediaMigrationDO finalAudit = audit;
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                fileMapper.updateById(new FileDO().setId(file.getId()).setConfigId(target.getId())
                        .setPath(finalAudit.getTargetPath()).setUrl(url));
                migrationMapper.updateById(new MediaMigrationDO().setId(finalAudit.getId())
                        .setStatus(SWITCHED).setSwitchedAt(LocalDateTimeUtil.now()).setLastError(null));
            });
            return MigrationOutcome.asSwitched();
        } catch (Exception ex) {
            migrationMapper.updateById(new MediaMigrationDO().setId(audit.getId()).setStatus(FAILED)
                    .setLastError(safeMessage(ex)));
            log.warn("[migrateOne][media migration failed fileId={} targetConfigId={}]",
                    file.getId(), target.getId(), ex);
            return MigrationOutcome.asFailed();
        }
    }

    private static String targetPath(FileDO file) {
        String extension = FileUtil.extName(file.getPath());
        return "migrated/" + file.getAssetKey() + (StrUtil.isBlank(extension) ? "" : "." + extension);
    }

    private static String safeMessage(Exception ex) {
        String value = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "no detail" : ex.getMessage());
        return value.substring(0, Math.min(value.length(), 950));
    }

    private record MigrationOutcome(boolean copied, boolean switched, boolean skipped, boolean failed) {
        static MigrationOutcome asCopied() { return new MigrationOutcome(true, false, false, false); }
        static MigrationOutcome asSwitched() { return new MigrationOutcome(false, true, false, false); }
        static MigrationOutcome asSkipped() { return new MigrationOutcome(false, false, true, false); }
        static MigrationOutcome asFailed() { return new MigrationOutcome(false, false, false, true); }
    }
}
