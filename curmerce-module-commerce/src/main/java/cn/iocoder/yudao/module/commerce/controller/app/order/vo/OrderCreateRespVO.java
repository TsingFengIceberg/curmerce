package cn.iocoder.yudao.module.commerce.controller.app.order.vo;

import lombok.Data;

@Data
public class OrderCreateRespVO {
    private Long orderId;
    private String orderNo;
    private Integer status;
    private Long payableAmount;
}
