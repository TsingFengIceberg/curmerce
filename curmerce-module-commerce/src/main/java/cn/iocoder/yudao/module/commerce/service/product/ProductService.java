package cn.iocoder.yudao.module.commerce.service.product;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductOperationLogRespVO;
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
    PageResult<ProductOperationLogRespVO> getOwnOperationLogPage(Long productId, PageParam pageParam);
    void submitOwnProduct(Long id);
    void listOwnProduct(Long id);
    void delistOwnProduct(Long id);
    ProductAggregate getProductForReview(Long id);
    PageResult<ProductAggregate> getProductReviewPage(ProductReviewPageReqVO reqVO);
    PageResult<ProductOperationLogRespVO> getReviewOperationLogPage(Long productId, PageParam pageParam);
    void approveProduct(Long id, Long reviewerId);
    void rejectProduct(ProductRejectReqVO reqVO, Long reviewerId);
}
