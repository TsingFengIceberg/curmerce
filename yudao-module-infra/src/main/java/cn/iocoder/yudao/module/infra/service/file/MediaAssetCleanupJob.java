package cn.iocoder.yudao.module.infra.service.file;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MediaAssetCleanupJob {

    @Resource
    private FileService fileService;

    @Resource
    private MediaUploadService mediaUploadService;

    @Scheduled(fixedDelayString = "${curmerce.media.cleanup-delay:PT1H}")
    public void cleanOrphans() {
        int count = fileService.cleanOrphanAssets();
        if (count > 0) log.info("[cleanOrphans][deleted {} unreferenced media assets]", count);
        int expiredTickets = mediaUploadService.expireTickets();
        if (expiredTickets > 0) log.info("[cleanOrphans][expired {} unfinished media upload tickets]", expiredTickets);
    }
}
