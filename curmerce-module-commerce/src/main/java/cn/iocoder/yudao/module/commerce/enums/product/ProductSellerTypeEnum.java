package cn.iocoder.yudao.module.commerce.enums.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductSellerTypeEnum {
    MERCHANT(1, "商家商品"),
    PERSONAL(2, "个人卖家商品");

    private final Integer type;
    private final String name;
}
