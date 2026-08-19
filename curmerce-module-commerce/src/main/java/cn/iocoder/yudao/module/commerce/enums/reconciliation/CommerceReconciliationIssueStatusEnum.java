package cn.iocoder.yudao.module.commerce.enums.reconciliation;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum CommerceReconciliationIssueStatusEnum implements ArrayValuable<Integer> {
    OPEN(10, "待处理"),
    RESOLVED(20, "已解决");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(CommerceReconciliationIssueStatusEnum::getStatus)
            .toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
