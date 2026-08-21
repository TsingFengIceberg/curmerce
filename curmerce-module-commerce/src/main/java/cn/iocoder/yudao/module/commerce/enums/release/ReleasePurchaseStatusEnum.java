package cn.iocoder.yudao.module.commerce.enums.release;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReleasePurchaseStatusEnum {
    PENDING(10, "待支付"),
    PAID(20, "已支付"),
    CANCELED(30, "已取消");

    private final Integer status;
    private final String name;
}
