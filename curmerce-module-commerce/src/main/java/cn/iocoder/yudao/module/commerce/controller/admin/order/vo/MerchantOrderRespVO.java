package cn.iocoder.yudao.module.commerce.controller.admin.order.vo;

import cn.iocoder.yudao.module.commerce.controller.app.order.vo.OrderItemRespVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MerchantOrderRespVO {
    private Long id;
    private String orderNo;
    private Long memberUserId;
    private String buyerMobile;
    private String buyerNickname;
    private String buyerEmail;
    private Long merchantId;
    private Long storeId;
    private Integer status;
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
    private LocalDateTime createTime;
    private List<OrderItemRespVO> items;
}
