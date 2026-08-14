package cn.iocoder.yudao.module.commerce.enums.product;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * Product storefront availability state.
 */
@Getter
@AllArgsConstructor
public enum ProductSaleStatusEnum implements ArrayValuable<Integer> {

    OFF_SHELF(0, "下架"),
    ON_SALE(1, "上架");

    public static final Integer[] ARRAYS = Arrays.stream(values())
            .map(ProductSaleStatusEnum::getStatus).toArray(Integer[]::new);

    private final Integer status;
    private final String name;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
