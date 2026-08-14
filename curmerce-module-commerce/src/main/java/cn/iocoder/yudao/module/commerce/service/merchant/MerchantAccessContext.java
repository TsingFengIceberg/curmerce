package cn.iocoder.yudao.module.commerce.service.merchant;

import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.store.StoreDO;

/**
 * Server-derived merchant identity used by merchant self-service commands.
 */
public record MerchantAccessContext(MerchantDO merchant, StoreDO store) {
}
