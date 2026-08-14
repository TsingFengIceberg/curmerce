package cn.iocoder.yudao.module.commerce.service.product;

import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;

import java.util.List;

public record ProductAggregate(ProductDO product, List<ProductSkuDO> skus) {
}
