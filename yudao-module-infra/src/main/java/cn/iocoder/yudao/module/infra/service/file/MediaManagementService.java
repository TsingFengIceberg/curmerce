package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.enums.file.MediaAssetStatus;
import cn.iocoder.yudao.module.infra.enums.file.MediaModerationStatus;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_NOT_EXISTS;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_ASSET_STATE_INVALID;

@Service
public class MediaManagementService {

    @Resource private FileMapper fileMapper;
    @Resource private ApplicationEventPublisher eventPublisher;
    @Resource private MediaMetrics metrics;

    @Transactional(rollbackFor = Exception.class)
    public void quarantine(Long id, String reason) {
        updateModeration(id, MediaAssetStatus.QUARANTINED, MediaModerationStatus.REVIEW_REQUIRED,
                defaultReason(reason, "Manually quarantined"), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        updateModeration(id, MediaAssetStatus.QUARANTINED, MediaModerationStatus.REJECTED,
                defaultReason(reason, "Rejected by administrator"), false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void release(Long id, String reason) {
        FileDO file = original(id);
        if (!Integer.valueOf(MediaAssetStatus.QUARANTINED.getStatus()).equals(file.getAssetStatus())) {
            throw exception(FILE_ASSET_STATE_INVALID);
        }
        updateModeration(id, MediaAssetStatus.READY, MediaModerationStatus.SAFE,
                defaultReason(reason, "Released by administrator"), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public void retry(Long id) {
        FileDO file = original(id);
        fileMapper.updateById(new FileDO().setId(file.getId())
                .setAssetStatus(MediaAssetStatus.PROCESSING.getStatus())
                .setModerationStatus(MediaModerationStatus.PENDING.getStatus())
                .setModerationReason(null).setModeratedBy(getLoginUserId()).setModeratedAt(LocalDateTimeUtil.now()));
        eventPublisher.publishEvent(new MediaAssetIngestedEvent(file.getId()));
    }

    private void updateModeration(Long id, MediaAssetStatus assetStatus,
                                  MediaModerationStatus moderationStatus, String reason, boolean ready) {
        FileDO file = original(id);
        Long operatorId = getLoginUserId();
        fileMapper.updateById(new FileDO().setId(file.getId()).setAssetStatus(assetStatus.getStatus())
                .setModerationStatus(moderationStatus.getStatus()).setModerationReason(reason)
                .setModeratedBy(operatorId).setModeratedAt(LocalDateTimeUtil.now()));
        for (FileDO variant : fileMapper.selectVariants(file.getId())) {
            fileMapper.updateById(new FileDO().setId(variant.getId()).setAssetStatus(assetStatus.getStatus())
                    .setModerationStatus(moderationStatus.getStatus()).setModerationReason(reason)
                    .setModeratedBy(operatorId).setModeratedAt(LocalDateTimeUtil.now()));
        }
        metrics.moderated("admin-" + moderationStatus.name().toLowerCase());
        if (ready) eventPublisher.publishEvent(new MediaAssetReadyEvent(file.getId()));
    }

    private FileDO original(Long id) {
        FileDO file = fileMapper.selectById(id);
        if (file == null) throw exception(FILE_NOT_EXISTS);
        return file.getOriginalFileId() == null ? file : fileMapper.selectById(file.getOriginalFileId());
    }

    private static String defaultReason(String reason, String fallback) {
        return StrUtil.isBlank(reason) ? fallback : reason.trim();
    }
}
