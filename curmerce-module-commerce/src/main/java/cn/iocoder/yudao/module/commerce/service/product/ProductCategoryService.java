package cn.iocoder.yudao.module.commerce.service.product;

import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryTreeRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryUpdateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryUpdateStatusReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;

import java.util.List;

public interface ProductCategoryService {
    Long createCategory(ProductCategoryCreateReqVO reqVO);
    void updateCategory(ProductCategoryUpdateReqVO reqVO);
    void updateCategoryStatus(ProductCategoryUpdateStatusReqVO reqVO);
    List<ProductCategoryTreeRespVO> getCategoryTree();
    List<ProductCategoryDO> lockCategorySnapshot();
    void requireEnabledCategory(Long categoryId, List<ProductCategoryDO> categories);
}
