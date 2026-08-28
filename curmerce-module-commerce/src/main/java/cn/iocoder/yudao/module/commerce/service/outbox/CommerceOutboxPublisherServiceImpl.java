package cn.iocoder.yudao.module.commerce.service.outbox;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.outbox.CommerceOutboxMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class CommerceOutboxPublisherServiceImpl implements CommerceOutboxPublisherService {

    /** 单事件最大发布尝试次数，超过后标记 DEAD。 */
    private static final int MAX_ATTEMPTS = 5;
    /** 首次重试等待秒数，之后按 2^(n-1) 指数退避。 */
    private static final long BASE_RETRY_SECONDS = 30L;

    @Resource
    private CommerceOutboxMapper outboxMapper;
    @Resource
    private CommerceOutboxMessagePublisher messagePublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int publishPending(int batchSize) {
        List<CommerceOutboxEventDO> events = outboxMapper.selectPendingForUpdate(batchSize);
        int published = 0;
        for (CommerceOutboxEventDO event : events) {
            try {
                messagePublisher.publish(event);
                if (outboxMapper.markPublished(event.getId(), LocalDateTime.now().withNano(0)) != 1) {
                    // 被其他发布器抢先处理，跳过即可。
                    continue;
                }
                published++;
            } catch (Exception ex) {
                handleFailure(event, ex);
            }
        }
        return published;
    }

    private void handleFailure(CommerceOutboxEventDO event, Exception ex) {
        int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
        String error = StrUtil.maxLength(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), 500);
        log.warn("[publishPending][事件({}:{}) 第 {} 次发布失败：{}]",
                event.getEventType(), event.getEventKey(), attempts, error, ex);
        if (attempts >= MAX_ATTEMPTS) {
            outboxMapper.markDead(event.getId(), error);
        } else {
            long backoffSeconds = BASE_RETRY_SECONDS * (1L << (attempts - 1));
            outboxMapper.markRetry(event.getId(), attempts,
                    LocalDateTime.now().plusSeconds(backoffSeconds).withNano(0), error);
        }
    }
}
