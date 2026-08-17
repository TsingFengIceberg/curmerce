package cn.iocoder.yudao.module.commerce.controller.app.order.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderCreateReqVO {
    @NotNull(message = "收货地址不能为空")
    private Long addressId;
}
