package cn.iocoder.yudao.module.commerce.dal.dataobject.order;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("commerce_order")
@KeySequence("commerce_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommerceOrderDO extends BaseDO {
    @TableId private Long id;
    private String orderNo;
    private Long memberUserId;
    private Long merchantId;
    private Long storeId;
    private Integer sellerType;
    private Long sellerUserId;
    private String idempotencyKey;
    private Integer status;
    /** 售后退款状态，与订单主交易状态独立。 */
    private Integer refundStatus;
    private LocalDateTime paymentDeadline;
    private Integer itemCount;
    private Long totalAmount;
    private Long payableAmount;
    private String receiverName;
    private String receiverMobile;
    private Integer receiverAreaId;
    private String receiverAreaName;
    private String receiverDetailAddress;
    private LocalDateTime shippingTime;
    private String logisticsCompany;
    private String trackingNo;
    private LocalDateTime completionTime;
}
