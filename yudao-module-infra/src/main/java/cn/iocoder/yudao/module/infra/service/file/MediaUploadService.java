package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.infra.controller.app.file.vo.MediaUploadCapabilitiesRespVO;
import cn.iocoder.yudao.module.infra.controller.app.file.vo.MediaUploadTicketReqVO;
import cn.iocoder.yudao.module.infra.controller.app.file.vo.MediaUploadTicketRespVO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.MediaUploadTicketDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.mysql.file.MediaUploadTicketMapper;
import cn.iocoder.yudao.module.infra.enums.file.MediaAssetStatus;
import cn.iocoder.yudao.module.infra.framework.file.config.CurmerceMediaProperties;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileObjectMetadata;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static cn.iocoder.yudao.module.infra.enums.file.MediaModerationStatus.PENDING;
import static cn.iocoder.yudao.module.infra.enums.file.MediaUploadTicketStatus.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class MediaUploadService {

    @Resource private FileConfigService fileConfigService;
    @Resource private FileMapper fileMapper;
    @Resource private MediaUploadTicketMapper ticketMapper;
    @Resource private MediaImageInspector imageInspector;
    @Resource private MediaContentScanner contentScanner;
    @Resource private MediaQuotaService quotaService;
    @Resource private CurmerceMediaProperties properties;
    @Resource private ApplicationEventPublisher eventPublisher;
    @Resource private MediaMetrics metrics;
    @Resource private PlatformTransactionManager transactionManager;

    public MediaUploadCapabilitiesRespVO getCapabilities() {
        FileClient client = fileConfigService.getMasterFileClient();
        Set<String> variants = !properties.isDerivativesEnabled() ? Set.of()
                : properties.getImgproxy().isEnabled()
                ? Set.of("thumb-webp", "card-webp", "thumb-avif", "card-avif")
                : Set.of("thumb-webp", "card-webp");
        return new MediaUploadCapabilitiesRespVO(client != null && client.supportsPresignedUpload(),
                properties.getMaxUploadSize().toBytes(), Set.copyOf(properties.getAllowedMimeTypes()),
                variants);
    }

    @Transactional(rollbackFor = Exception.class)
    public MediaUploadTicketRespVO issueTicket(MediaUploadTicketReqVO request) {
        String name = FilePathUtils.validateFileName(request.getName());
        FilePathUtils.validateDirectory(request.getDirectory());
        String contentType = request.getContentType().trim().toLowerCase();
        if (!properties.getAllowedMimeTypes().contains(contentType)) {
            throw exception(FILE_TYPE_NOT_ALLOWED);
        }
        if (request.getSize() > properties.getMaxUploadSize().toBytes()) {
            throw exception(FILE_UPLOAD_TOO_LARGE, properties.getMaxUploadSize().toMegabytes());
        }
        MediaQuotaReservation reservation = quotaService.reserve(request.getSize());
        FileClient client = fileConfigService.getMasterFileClient();
        if (client == null || !client.supportsPresignedUpload()) {
            throw exception(FILE_DIRECT_UPLOAD_UNAVAILABLE);
        }

        String ticketKey = UUID.randomUUID().toString();
        String assetKey = UUID.randomUUID().toString();
        String path = buildPath(request.getDirectory(), assetKey, MediaImageInspector.extension(contentType));
        LocalDateTime expiresAt = LocalDateTimeUtil.now().plus(properties.getUploadTicketTtl());
        int ttlSeconds = Math.toIntExact(properties.getUploadTicketTtl().toSeconds());
        String uploadUrl = client.presignPutUrl(path, contentType, request.getSize(), ttlSeconds);
        ticketMapper.insert(new MediaUploadTicketDO().setTicketKey(ticketKey).setAssetKey(assetKey)
                .setConfigId(client.getId()).setPath(path).setOriginalName(name)
                .setExpectedType(contentType).setExpectedSize(request.getSize())
                .setDirectory(request.getDirectory()).setVisibility(request.getVisibility())
                .setOwnerUserId(reservation.userId()).setOwnerUserType(reservation.userType())
                .setQuotaDate(reservation.quotaDate()).setStatus(ISSUED.getStatus()).setExpiresAt(expiresAt));
        metrics.directTicketIssued();
        return new MediaUploadTicketRespVO(ticketKey, uploadUrl,
                Map.of("Content-Type", contentType), FileServiceImpl.stableAssetUrl(assetKey), expiresAt);
    }

    public String finalizeTicket(String ticketKey) {
        MediaUploadTicketDO ticket = validateTicketOwner(ticketKey);
        if (Integer.valueOf(FINALIZED.getStatus()).equals(ticket.getStatus())) {
            FileDO file = fileMapper.selectById(ticket.getFinalizedFileId());
            if (file != null) return FileServiceImpl.stableAssetUrl(file.getAssetKey());
        }
        if (!Integer.valueOf(ISSUED.getStatus()).equals(ticket.getStatus())) {
            throw exception(FILE_UPLOAD_TICKET_STATE_INVALID);
        }
        if (ticket.getExpiresAt().isBefore(LocalDateTimeUtil.now())) {
            expireTicket(ticket, true);
            throw exception(FILE_UPLOAD_TICKET_EXPIRED);
        }
        if (ticketMapper.claim(ticket.getId()) != 1) {
            throw exception(FILE_UPLOAD_TICKET_STATE_INVALID);
        }

        FileClient client = fileConfigService.getFileClient(ticket.getConfigId());
        Assert.notNull(client, "客户端({}) 不能为空", ticket.getConfigId());
        try {
            FileObjectMetadata object = client.getMetadata(ticket.getPath());
            if (object == null) {
                ticketMapper.releaseClaim(ticket.getId());
                throw exception(FILE_DIRECT_UPLOAD_MISSING);
            }
            if (object.size() != ticket.getExpectedSize()
                    || object.size() > properties.getMaxUploadSize().toBytes()
                    || (StrUtil.isNotBlank(object.contentType())
                    && !ticket.getExpectedType().equalsIgnoreCase(object.contentType()))) {
                rejectTicket(ticket, client, "Uploaded object metadata does not match the ticket");
                throw exception(FILE_DIRECT_UPLOAD_MISMATCH);
            }
            byte[] content = client.getContent(ticket.getPath());
            if (content == null || content.length != ticket.getExpectedSize()) {
                rejectTicket(ticket, client, "Uploaded object bytes do not match the ticket");
                throw exception(FILE_DIRECT_UPLOAD_MISMATCH);
            }
            MediaImageMetadata metadata;
            try {
                metadata = imageInspector.inspect(content, ticket.getOriginalName());
            } catch (RuntimeException ex) {
                rejectTicket(ticket, client, "Image validation failed: " + safeReason(ex.getMessage()));
                throw ex;
            }
            if (!ticket.getExpectedType().equals(metadata.mimeType())) {
                rejectTicket(ticket, client, "Detected MIME type does not match the ticket");
                throw exception(FILE_DIRECT_UPLOAD_MISMATCH);
            }
            MediaScanResult scan = contentScanner.scan(content);
            if (scan.status() == MediaScanResult.Status.REJECTED) {
                rejectTicket(ticket, client, scan.detail());
                throw exception(FILE_SCAN_REJECTED);
            }
            if (scan.status() == MediaScanResult.Status.ERROR) {
                ticketMapper.releaseClaim(ticket.getId());
                throw exception(FILE_SCAN_UNAVAILABLE);
            }
            String sha256 = DigestUtil.sha256Hex(content);
            String dedupKey = FileServiceImpl.dedupKey(sha256, ticket.getVisibility(),
                    ticket.getOwnerUserId(), ticket.getOwnerUserType());
            FileDO duplicate = fileMapper.selectOriginalByDedupKey(dedupKey);
            if (duplicate != null) {
                if (!isReusable(duplicate)) {
                    rejectTicket(ticket, client, "Duplicate content belongs to a non-ready asset");
                    throw exception(FILE_ASSET_NOT_READY);
                }
                completeDuplicate(ticket, duplicate);
                tryDelete(client, ticket.getPath());
                metrics.deduplicated(content.length);
                metrics.directFinalized();
                return FileServiceImpl.stableAssetUrl(duplicate.getAssetKey());
            }
            FileDO file = new FileDO().setAssetKey(ticket.getAssetKey()).setConfigId(ticket.getConfigId())
                    .setName(ticket.getOriginalName()).setPath(ticket.getPath())
                    .setUrl(client.presignGetUrl(ticket.getPath(), null))
                    .setType(metadata.mimeType()).setSize((long) content.length).setSha256(sha256).setDedupKey(dedupKey)
                    .setAssetStatus(MediaAssetStatus.PROCESSING.getStatus()).setScanStatus(scan.status() == MediaScanResult.Status.CLEAN ? 10 : 30)
                    .setModerationStatus(PENDING.getStatus())
                    .setVisibility(ticket.getVisibility()).setOwnerUserId(ticket.getOwnerUserId())
                    .setOwnerUserType(ticket.getOwnerUserType()).setWidth(metadata.width()).setHeight(metadata.height())
                    .setOrphanedAt(LocalDateTimeUtil.now());
            try {
                TransactionTemplate transaction = new TransactionTemplate(transactionManager);
                transaction.executeWithoutResult(status -> {
                    fileMapper.insert(file);
                    ticketMapper.updateById(new MediaUploadTicketDO().setId(ticket.getId())
                            .setStatus(FINALIZED.getStatus()).setFinalizedFileId(file.getId()).setFailureReason(null));
                    quotaService.commitStorage(reservation(ticket));
                    eventPublisher.publishEvent(new MediaAssetIngestedEvent(file.getId()));
                });
            } catch (DuplicateKeyException ex) {
                FileDO concurrentDuplicate = fileMapper.selectOriginalByDedupKey(dedupKey);
                if (concurrentDuplicate == null) throw ex;
                if (!isReusable(concurrentDuplicate)) {
                    rejectTicket(ticket, client, "Duplicate content belongs to a non-ready asset");
                    throw exception(FILE_ASSET_NOT_READY);
                }
                completeDuplicate(ticket, concurrentDuplicate);
                tryDelete(client, ticket.getPath());
                metrics.deduplicated(content.length);
                metrics.directFinalized();
                return FileServiceImpl.stableAssetUrl(concurrentDuplicate.getAssetKey());
            }
            metrics.stored(content.length);
            metrics.directFinalized();
            return FileServiceImpl.stableAssetUrl(file.getAssetKey());
        } catch (RuntimeException ex) {
            MediaUploadTicketDO current = ticketMapper.selectById(ticket.getId());
            if (current != null && Integer.valueOf(PROCESSING.getStatus()).equals(current.getStatus())) {
                ticketMapper.releaseClaim(ticket.getId());
            }
            throw ex;
        } catch (Exception ex) {
            ticketMapper.releaseClaim(ticket.getId());
            throw new IllegalStateException("Unable to finalize direct media upload", ex);
        }
    }

    public int expireTickets() {
        int expired = 0;
        LocalDateTime now = LocalDateTimeUtil.now();
        LocalDateTime staleBefore = now.minus(properties.getUploadTicketProcessingLease());
        for (MediaUploadTicketDO ticket : ticketMapper.selectExpired(now, staleBefore, 100)) {
            if (ticketMapper.claimExpired(ticket.getId(), now, staleBefore) != 1) continue;
            expireTicket(ticket, false);
            expired++;
        }
        return expired;
    }

    private void completeDuplicate(MediaUploadTicketDO ticket, FileDO duplicate) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            ticketMapper.updateById(new MediaUploadTicketDO().setId(ticket.getId())
                    .setStatus(FINALIZED.getStatus()).setFinalizedFileId(duplicate.getId()).setFailureReason(null));
            quotaService.commitStorage(reservation(ticket));
        });
    }

    private void rejectTicket(MediaUploadTicketDO ticket, FileClient client, String reason) {
        tryDelete(client, ticket.getPath());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            ticketMapper.updateById(new MediaUploadTicketDO().setId(ticket.getId())
                    .setStatus(REJECTED.getStatus()).setFailureReason(safeReason(reason)));
            quotaService.commitStorage(reservation(ticket));
        });
        metrics.directRejected();
    }

    private void expireTicket(MediaUploadTicketDO ticket, boolean claim) {
        if (claim && ticketMapper.claim(ticket.getId()) != 1) {
            throw exception(FILE_UPLOAD_TICKET_STATE_INVALID);
        }
        FileClient client = fileConfigService.getFileClient(ticket.getConfigId());
        if (client != null) tryDelete(client, ticket.getPath());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            ticketMapper.updateById(new MediaUploadTicketDO().setId(ticket.getId()).setStatus(EXPIRED.getStatus()));
            quotaService.release(reservation(ticket));
        });
    }

    private MediaUploadTicketDO validateTicketOwner(String ticketKey) {
        MediaUploadTicketDO ticket = ticketMapper.selectByTicketKey(ticketKey);
        if (ticket == null) throw exception(FILE_UPLOAD_TICKET_NOT_FOUND);
        LoginUser user = getLoginUser();
        if (user == null || !user.getId().equals(ticket.getOwnerUserId())
                || !user.getUserType().equals(ticket.getOwnerUserType())) {
            throw exception(FILE_UPLOAD_TICKET_FORBIDDEN);
        }
        return ticket;
    }

    private static MediaQuotaReservation reservation(MediaUploadTicketDO ticket) {
        return new MediaQuotaReservation(ticket.getOwnerUserId(), ticket.getOwnerUserType(),
                ticket.getQuotaDate(), ticket.getExpectedSize());
    }

    private static boolean isReusable(FileDO file) {
        return Integer.valueOf(MediaAssetStatus.PROCESSING.getStatus()).equals(file.getAssetStatus())
                || Integer.valueOf(MediaAssetStatus.READY.getStatus()).equals(file.getAssetStatus());
    }

    private static String buildPath(String directory, String assetKey, String extension) {
        String path = LocalDateTimeUtil.format(LocalDateTimeUtil.now(), "yyyyMMdd")
                + "/" + assetKey + "." + extension;
        return StrUtil.isBlank(directory) ? path : directory + "/" + path;
    }

    private static String safeReason(String reason) {
        if (reason == null) return "Rejected by media validation";
        return reason.substring(0, Math.min(reason.length(), 500));
    }

    private void tryDelete(FileClient client, String path) {
        try {
            client.delete(path);
        } catch (Exception ex) {
            log.warn("[tryDelete][failed to remove rejected direct upload path={}]", path, ex);
        }
    }
}
