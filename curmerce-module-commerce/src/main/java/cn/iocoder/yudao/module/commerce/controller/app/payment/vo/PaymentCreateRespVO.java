package cn.iocoder.yudao.module.commerce.controller.app.payment.vo;

import lombok.Data;

@Data
public class PaymentCreateRespVO {
    private Long paymentId;
    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private Long amount;
    private Integer status;
}
