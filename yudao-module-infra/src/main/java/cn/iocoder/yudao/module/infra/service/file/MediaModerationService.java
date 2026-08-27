package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.enums.file.MediaAssetStatus;
import cn.iocoder.yudao.module.infra.enums.file.MediaModerationStatus;
import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class MediaModerationService {

    @Resource private FileMapper fileMapper;
    @Resource private FileConfigService fileConfigService;
    @Resource private CurmerceMediaProperties properties;
    @Resource private MediaContentModerator moderator;
    @Resource private ApplicationEventPublisher eventPublisher;
    @Resource private MediaMetrics metrics;
    @Resource private PlatformTransactionManager transactionManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIngested(MediaAssetIngestedEvent event) {
        FileDO file = fileMapper.selectById(event.fileId());
        if (file == null || file.getOriginalFileId() != null) return;
        if (!properties.getModeration().isEnabled()) {
            markReady(file, MediaModerationStatus.SKIPPED, "Content moderation disabled");
            return;
        }
        try {
            FileClient client = fileConfigService.getFileClient(file.getConfigId());
            byte[] content = client.getContent(file.getPath());
            MediaModerationDecision decision = moderator.moderate(content, file.getType(), file.getSha256());
            switch (decision.status()) {
                case SAFE -> markReady(file, MediaModerationStatus.SAFE, decision.reason());
                case REVIEW -> quarantine(file, MediaModerationStatus.REVIEW_REQUIRED, decision.reason());
                case REJECT -> quarantine(file, MediaModerationStatus.REJECTED, decision.reason());
                case ERROR -> {
                    if (properties.getModeration().isFailClosed()) {
                        quarantine(file, MediaModerationStatus.ERROR, decision.reason());
                    } else {
                        markReady(file, MediaModerationStatus.ERROR, decision.reason());
                    }
                }
            }
        } catch (Exception ex) {
            String reason = "Moderation processing failed: " + safeMessage(ex);
            if (properties.getModeration().isFailClosed()) {
                quarantine(file, MediaModerationStatus.ERROR, reason);
            } else {
                markReady(file, MediaModerationStatus.ERROR, reason);
            }
            log.warn("[onIngested][moderation failed for fileId={}]", file.getId(), ex);
        }
    }

    private void markReady(FileDO file, MediaModerationStatus status, String reason) {
        String safeReason = safeReason(reason);
        java.time.LocalDateTime moderatedAt = LocalDateTimeUtil.now();
        new TransactionTemplate(transactionManager).executeWithoutResult(transactionStatus -> {
            fileMapper.updateById(new FileDO().setId(file.getId())
                    .setAssetStatus(MediaAssetStatus.READY.getStatus())
                    .setModerationStatus(status.getStatus()).setModerationReason(safeReason)
                    .setModeratedAt(moderatedAt));
            for (FileDO variant : fileMapper.selectVariants(file.getId())) {
                fileMapper.updateById(new FileDO().setId(variant.getId())
                        .setAssetStatus(MediaAssetStatus.READY.getStatus())
                        .setModerationStatus(status.getStatus()).setModerationReason(safeReason)
                        .setModeratedAt(moderatedAt));
            }
            eventPublisher.publishEvent(new MediaAssetReadyEvent(file.getId()));
        });
        metrics.moderated(status.name().toLowerCase());
    }

    private void quarantine(FileDO file, MediaModerationStatus status, String reason) {
        String safeReason = safeReason(reason);
        java.time.LocalDateTime moderatedAt = LocalDateTimeUtil.now();
        new TransactionTemplate(transactionManager).executeWithoutResult(transactionStatus -> {
            fileMapper.updateById(new FileDO().setId(file.getId())
                    .setAssetStatus(MediaAssetStatus.QUARANTINED.getStatus())
                    .setModerationStatus(status.getStatus()).setModerationReason(safeReason)
                    .setModeratedAt(moderatedAt));
            for (FileDO variant : fileMapper.selectVariants(file.getId())) {
                fileMapper.updateById(new FileDO().setId(variant.getId())
                        .setAssetStatus(MediaAssetStatus.QUARANTINED.getStatus())
                        .setModerationStatus(status.getStatus()).setModerationReason(safeReason)
                        .setModeratedAt(moderatedAt));
            }
        });
        metrics.moderated(status.name().toLowerCase());
    }

    private static String safeReason(String reason) {
        if (reason == null) return null;
        return reason.substring(0, Math.min(reason.length(), 500));
    }

    private static String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 450));
    }
}
