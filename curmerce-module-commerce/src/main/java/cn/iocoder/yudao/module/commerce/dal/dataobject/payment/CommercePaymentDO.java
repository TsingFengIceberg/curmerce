package cn.iocoder.yudao.module.commerce.dal.dataobject.payment;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("commerce_payment")
@KeySequence("commerce_payment_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommercePaymentDO extends BaseDO {
    @TableId
    private Long id;
    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private Long memberUserId;
    private Long amount;
    private Integer status;
    private String callbackId;
    private LocalDateTime paidTime;
}
