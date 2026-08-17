package cn.iocoder.yudao.module.commerce.enums.order;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OrderStatusEnum {
    PENDING_PAYMENT(10, "待支付");

    private final Integer status;
    private final String name;
}
