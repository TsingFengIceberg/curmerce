package cn.iocoder.yudao.module.commerce.service.outbox;

import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;

/**
 * Transport-neutral boundary for publishing a committed commerce Outbox event.
 */
public interface CommerceOutboxMessagePublisher {

    void publish(CommerceOutboxEventDO event);
}
