package cn.iocoder.yudao.module.commerce.enums.order;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum OrderStatusEnum implements ArrayValuable<Integer> {
    PENDING_PAYMENT(10, "待支付"),
    PAID_PENDING_SHIPMENT(20, "已支付待发货"),
    SHIPPED(30, "已发货"),
    COMPLETED(40, "已完成"),
    CANCELED(50, "已取消");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(OrderStatusEnum::getStatus)
            .toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
