package cn.iocoder.yudao.module.commerce.dal.dataobject.release;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Durable fence for a Redis limited-release reservation.
 *
 * <p>The Redis gate is deliberately acquired before the hot MySQL row lock,
 * but a database transaction can still fail after the gate succeeds.  This
 * row is written in the same transaction as the purchase.  A committed row
 * lets a recovery worker finish the Redis commit after a process crash; an
 * absent row lets the Redis lease cleaner release an orphan reservation.</p>
 */
@TableName("commerce_release_reservation")
@KeySequence("commerce_release_reservation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceReleaseReservationDO extends BaseDO {
    public static final int COMMITTED = 20;
    public static final int FINALIZED = 30;
    /** Terminal operator-visible state after bounded Redis recovery retries. */
    public static final int DEAD = 40;

    @TableId private Long id;
    /** Explicit tenant fence because this table intentionally does not rely on
     * the shared foundation's TenantBaseDO interceptor. */
    private String tenantId;
    private String reservationKey;
    private Long campaignId;
    private Long itemId;
    private Long buyerUserId;
    private Long purchaseId;
    private Integer quantity;
    private Integer status;
    private Integer attempts;
    private LocalDateTime nextRetryAt;
    private String lastError;
}
