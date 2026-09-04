package cn.iocoder.yudao.module.commerce.dal.dataobject.outbox;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("commerce_outbox_event")
@KeySequence("commerce_outbox_event_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceOutboxEventDO extends BaseDO {
    @TableId private Long id;
    /** Stable tenant value captured at append time for asynchronous publishers. */
    private String tenantId;
    private String eventType;
    private String eventKey;
    private String aggregateType;
    private Long aggregateId;
    private String payload;
    private Integer status;
    private Integer attempts;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private LocalDateTime publishedTime;
}
