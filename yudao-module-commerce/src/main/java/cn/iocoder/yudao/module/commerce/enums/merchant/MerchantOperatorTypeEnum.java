package cn.iocoder.yudao.module.commerce.enums.merchant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MerchantOperatorTypeEnum {
    OWNER(1);
    private final Integer type;
}
