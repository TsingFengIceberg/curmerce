package cn.iocoder.yudao.module.commerce.service.outbox;

public interface CommerceOutboxPublisherService {

    /**
     * 领取一批 PENDING 事件并发布到 Redis Stream，成功后标记 PUBLISHED。
     * 发送失败按尝试次数指数退避，超过上限标记 DEAD 等待人工处理。
     *
     * @param batchSize 单批事件数
     * @return 成功发布并标记的事件数
     */
    int publishPending(int batchSize);
}
