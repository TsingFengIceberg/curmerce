package cn.iocoder.yudao.module.commerce.controller.app.order.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderDetailRespVO extends OrderSummaryRespVO {
    private String receiverName;
    private String receiverMobile;
    private Integer receiverAreaId;
    private String receiverAreaName;
    private String receiverDetailAddress;
    private LocalDateTime shippingTime;
    private LocalDateTime completionTime;
    private String logisticsCompany;
    private String trackingNo;
    private List<OrderItemRespVO> items;
    private RefundSummaryRespVO refund;
}
