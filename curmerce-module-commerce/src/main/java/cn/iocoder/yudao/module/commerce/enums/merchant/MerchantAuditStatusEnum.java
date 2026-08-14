package cn.iocoder.yudao.module.commerce.enums.merchant;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum MerchantAuditStatusEnum implements ArrayValuable<Integer> {
    PENDING(0, "待审核"), APPROVED(1, "已通过"), REJECTED(2, "已拒绝");

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(MerchantAuditStatusEnum::getStatus).toArray(Integer[]::new);
    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
