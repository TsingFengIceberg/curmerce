package cn.iocoder.yudao.module.commerce.service.outbox;

import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommerceOutboxPublisherJob {

    @Resource
    private CommerceOutboxPublisherService publisherService;

    @Scheduled(fixedDelayString = "${curmerce.outbox.publish-delay-ms:5000}")
    public void publishPending() {
        publisherService.publishPending(100);
    }
}
