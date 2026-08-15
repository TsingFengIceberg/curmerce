package cn.iocoder.yudao.module.commerce.service.catalog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.catalog.vo.*;
import java.util.List;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;

public interface PublicCatalogService {
    List<PublicCategoryNodeRespVO> getCategoryTree();
    PageResult<PublicProductSummaryRespVO> getProductPage(PublicProductPageReqVO reqVO);
    PublicProductDetailRespVO getProductDetail(Long id);
    PublicProductSummaryRespVO getVisibleSummary(Long productId, Long skuId);
    ProductSkuDO getVisibleSku(Long skuId);
}
