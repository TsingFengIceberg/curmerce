package cn.iocoder.yudao.module.commerce.dal.dataobject.release;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Durable command for the optional Kafka-backed limited-release path.
 *
 * <p>The command is not a replacement for the purchase record or order. It
 * records delivery and compensation state around the existing local purchase
 * transaction, so duplicate Kafka deliveries and abandoned workers converge
 * on the same idempotency key.</p>
 */
@TableName("commerce_release_purchase_command")
@KeySequence("commerce_release_purchase_command_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceReleasePurchaseCommandDO extends BaseDO {
    @TableId private Long id;
    private String ticket;
    private Long buyerUserId;
    private Long itemId;
    private Integer quantity;
    private Long addressId;
    private String idempotencyKey;
    /** 10 QUEUED, 20 PROCESSING, 30 COMPLETED, 40 FAILED, 50 RETRY_WAIT. */
    private Integer status;
    private Integer attempts;
    private Integer dispatchVersion;
    private String processingToken;
    private LocalDateTime processingDeadline;
    private LocalDateTime retryAt;
    private String result;
    private String lastError;
}
