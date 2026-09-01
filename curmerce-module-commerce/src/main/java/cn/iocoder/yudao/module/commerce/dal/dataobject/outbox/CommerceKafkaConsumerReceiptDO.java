package cn.iocoder.yudao.module.commerce.dal.dataobject.outbox;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("commerce_kafka_consumer_receipt")
@KeySequence("commerce_kafka_consumer_receipt_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceKafkaConsumerReceiptDO extends BaseDO {
    @TableId private Long id;
    private String consumerGroup;
    private Long eventId;
    private String eventType;
    private String eventKey;
    private String payload;
    private Integer status;
    private Integer attempts;
    private LocalDateTime processingTime;
    private String lastError;
    private LocalDateTime receivedTime;
    private LocalDateTime processedTime;
}
