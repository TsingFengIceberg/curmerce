package cn.iocoder.yudao.module.commerce.enums.refund;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum RefundStatusEnum implements ArrayValuable<Integer> {
    NONE(0, "无售后"),
    REQUESTED(10, "退款申请中"),
    APPROVED(20, "退款通过/处理中"),
    SUCCESS(30, "退款成功"),
    REJECTED(40, "退款拒绝"),
    FAILED(50, "退款失败");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(RefundStatusEnum::getStatus)
            .toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
