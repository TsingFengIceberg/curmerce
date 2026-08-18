package cn.iocoder.yudao.module.commerce.controller.app.order.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderConfirmReceiptReqVO {
    @NotNull(message = "订单不能为空")
    private Long id;
}
