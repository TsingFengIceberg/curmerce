package cn.iocoder.yudao.module.commerce.controller.app.payment.vo;

import lombok.Data;

@Data
public class PaymentCallbackRespVO {
    private Long paymentId;
    private String paymentNo;
    private Long orderId;
    private Integer paymentStatus;
    private Integer orderStatus;
    private Long paidAmount;
    private String callbackId;
}
