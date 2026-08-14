package cn.iocoder.yudao.module.commerce.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductCreateOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductPageOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductRejectReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductReviewPageReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductUpdateOwnReqVO;

public interface ProductService {
    Long createOwnProduct(ProductCreateOwnReqVO reqVO);
    void updateOwnProduct(ProductUpdateOwnReqVO reqVO);
    ProductAggregate getOwnProduct(Long id);
    PageResult<ProductAggregate> getOwnProductPage(ProductPageOwnReqVO reqVO);
    void submitOwnProduct(Long id);
    void listOwnProduct(Long id);
    void delistOwnProduct(Long id);
    ProductAggregate getProductForReview(Long id);
    PageResult<ProductAggregate> getProductReviewPage(ProductReviewPageReqVO reqVO);
    void approveProduct(Long id);
    void rejectProduct(ProductRejectReqVO reqVO);
}
