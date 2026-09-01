package cn.iocoder.yudao.curmerce.cloud.api;

import lombok.Data;

import java.time.LocalDateTime;

/** Least-privilege order view exposed to the Agent service. */
@Data
public class CoreOrderStatusRespDTO {
    private Long orderId;
    private String orderNo;
    private Integer status;
    private Integer refundStatus;
    private Long payableAmount;
    private LocalDateTime createTime;
    private LocalDateTime shippingTime;
    private LocalDateTime completionTime;
    private String logisticsCompany;
    private String trackingNo;
}
