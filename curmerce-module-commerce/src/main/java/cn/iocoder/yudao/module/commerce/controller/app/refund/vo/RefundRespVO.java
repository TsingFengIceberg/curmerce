package cn.iocoder.yudao.module.commerce.controller.app.refund.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefundRespVO {
    private Long id;
    private String refundNo;
    private Long orderId;
    private String orderNo;
    private Long amount;
    private Integer status;
    private String reason;
    private LocalDateTime requestedTime;
    private Long reviewerUserId;
    private LocalDateTime reviewedTime;
    private String reviewRemark;
    private String callbackId;
    private Boolean callbackSuccess;
    private LocalDateTime processedTime;
}
