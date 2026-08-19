package cn.iocoder.yudao.module.commerce.enums.outbox;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum CommerceOutboxStatusEnum implements ArrayValuable<Integer> {
    PENDING(10, "待发布"),
    PUBLISHED(20, "已发布"),
    DEAD(30, "发布失败待人工处理");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CommerceOutboxStatusEnum::getStatus)
            .toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
