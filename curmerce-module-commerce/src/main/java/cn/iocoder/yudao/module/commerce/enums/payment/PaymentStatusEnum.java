package cn.iocoder.yudao.module.commerce.enums.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PaymentStatusEnum {
    INITIATED(10, "待支付"),
    SUCCESS(20, "支付成功");

    private final Integer status;
    private final String name;
}
