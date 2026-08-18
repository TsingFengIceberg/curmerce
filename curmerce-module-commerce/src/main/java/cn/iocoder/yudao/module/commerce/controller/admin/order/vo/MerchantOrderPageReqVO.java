package cn.iocoder.yudao.module.commerce.controller.admin.order.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Merchant pending-shipment orders are scoped entirely from the logged-in
 * merchant context; this request intentionally has no merchant or store ID.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantOrderPageReqVO extends PageParam {
}
