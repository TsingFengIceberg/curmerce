package cn.iocoder.yudao.module.commerce.controller.admin.product;

import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductSkuRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductSpecificationValueRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductDO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductSkuDO;
import cn.iocoder.yudao.module.commerce.service.product.ProductAggregate;

import java.util.List;

final class ProductRespAssembler {

    private ProductRespAssembler() {
    }

    static ProductRespVO toResponse(ProductAggregate aggregate) {
        ProductDO product = aggregate.product();
        ProductRespVO response = new ProductRespVO().setId(product.getId()).setMerchantId(product.getMerchantId())
                .setStoreId(product.getStoreId()).setSellerType(product.getSellerType()).setSellerUserId(product.getSellerUserId())
                .setCategoryId(product.getCategoryId()).setCode(product.getCode())
                .setName(product.getName()).setCondition(product.getCondition()).setSubtitle(product.getSubtitle()).setMainImageUrl(product.getMainImageUrl())
                .setImageUrls(product.getImageUrls()).setDescription(product.getDescription())
                .setAuditStatus(product.getAuditStatus()).setSaleStatus(product.getSaleStatus())
                .setReviewerUserId(product.getReviewerUserId()).setReviewTime(product.getReviewTime())
                .setRejectReason(product.getRejectReason()).setSort(product.getSort())
                .setCreateTime(product.getCreateTime()).setUpdateTime(product.getUpdateTime());
        response.setSkus(aggregate.skus().stream().map(ProductRespAssembler::toSkuResponse).toList());
        return response;
    }

    private static ProductSkuRespVO toSkuResponse(ProductSkuDO sku) {
        List<ProductSpecificationValueRespVO> specifications = sku.getSpecificationValues() == null ? List.of()
                : sku.getSpecificationValues().stream()
                .map(value -> new ProductSpecificationValueRespVO().setName(value.getName()).setValue(value.getValue()))
                .toList();
        return new ProductSkuRespVO().setId(sku.getId()).setProductId(sku.getProductId()).setCode(sku.getCode())
                .setSpecificationValues(specifications).setImageUrl(sku.getImageUrl()).setPrice(sku.getPrice())
                .setMarketPrice(sku.getMarketPrice()).setStock(sku.getStock()).setStatus(sku.getStatus())
                .setSort(sku.getSort());
    }
}
