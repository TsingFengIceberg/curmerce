package cn.iocoder.yudao.module.commerce.enums.refund;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RefundStatusEnum {
    REQUESTED(10, "退款申请中"),
    APPROVED(20, "退款通过/处理中"),
    SUCCESS(30, "退款成功"),
    REJECTED(40, "退款拒绝");

    private final Integer status;
    private final String name;
}
