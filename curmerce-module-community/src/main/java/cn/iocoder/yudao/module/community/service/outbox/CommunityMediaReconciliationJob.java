package cn.iocoder.yudao.module.community.service.outbox;

import cn.iocoder.yudao.module.community.dal.dataobject.post.CommunityPostDO;
import cn.iocoder.yudao.module.community.dal.mysql.post.CommunityPostMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CommunityMediaReconciliationJob {

    @Resource private CommunityPostMapper postMapper;
    @Resource private CommunityMediaOutboxService outboxService;
    @Value("${curmerce.community-media-outbox.batch-size:50}") private int batchSize;

    @Scheduled(fixedDelayString = "${curmerce.community-media-outbox.reconcile-delay-ms:60000}")
    @Transactional(rollbackFor = Exception.class)
    public void reconcilePosts() {
        int safeBatchSize = Math.max(1, Math.min(batchSize, 500));
        long cursor = 0L;
        while (true) {
            List<CommunityPostDO> posts = postMapper.selectMediaBatchAfterId(cursor, safeBatchSize);
            for (CommunityPostDO post : posts) {
                outboxService.recordDesiredState("community_post", post.getId().toString(), "media",
                        post.getMediaUrls());
            }
            if (posts.size() < safeBatchSize) {
                return;
            }
            cursor = posts.getLast().getId();
        }
    }
}
