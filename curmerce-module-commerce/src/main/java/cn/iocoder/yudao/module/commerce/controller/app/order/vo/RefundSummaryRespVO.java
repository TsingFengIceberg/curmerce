package cn.iocoder.yudao.module.commerce.controller.app.order.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RefundSummaryRespVO {
    private Long id;
    private String refundNo;
    private Long amount;
    private Integer status;
    private String reason;
    private LocalDateTime requestedTime;
    private LocalDateTime processedTime;
}
