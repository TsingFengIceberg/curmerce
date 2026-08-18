package cn.iocoder.yudao.module.commerce.dal.dataobject.refund;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("commerce_refund")
@KeySequence("commerce_refund_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceRefundDO extends BaseDO {
    @TableId
    private Long id;
    private String refundNo;
    private Long orderId;
    private String orderNo;
    private Long memberUserId;
    private Long amount;
    private Integer status;
    private String reason;
    private LocalDateTime requestedTime;
    private LocalDateTime processedTime;
}
