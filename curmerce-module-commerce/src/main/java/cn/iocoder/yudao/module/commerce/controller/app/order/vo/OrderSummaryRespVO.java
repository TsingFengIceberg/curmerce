package cn.iocoder.yudao.module.commerce.controller.app.order.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OrderSummaryRespVO {
    private Long id;
    private String orderNo;
    private Long merchantId;
    private Long storeId;
    private Integer status;
    private Integer itemCount;
    private Long totalAmount;
    private Long payableAmount;
    private LocalDateTime createTime;
    private LocalDateTime completionTime;
}
