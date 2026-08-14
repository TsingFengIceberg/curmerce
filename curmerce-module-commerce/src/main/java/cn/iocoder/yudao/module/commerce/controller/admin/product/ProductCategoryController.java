package cn.iocoder.yudao.module.commerce.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryTreeRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryUpdateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryUpdateStatusReqVO;
import cn.iocoder.yudao.module.commerce.service.product.ProductCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Curmerce 商品分类")
@RestController
@RequestMapping("/commerce/product-category")
@Validated
public class ProductCategoryController {

    @Resource
    private ProductCategoryService categoryService;

    @PostMapping("/create")
    @Operation(summary = "创建商品分类")
    @PreAuthorize("@ss.hasPermission('commerce:product-category:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProductCategoryCreateReqVO reqVO) {
        return success(categoryService.createCategory(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改商品分类")
    @PreAuthorize("@ss.hasPermission('commerce:product-category:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ProductCategoryUpdateReqVO reqVO) {
        categoryService.updateCategory(reqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "更新商品分类状态")
    @PreAuthorize("@ss.hasPermission('commerce:product-category:update')")
    public CommonResult<Boolean> updateStatus(@Valid @RequestBody ProductCategoryUpdateStatusReqVO reqVO) {
        categoryService.updateCategoryStatus(reqVO);
        return success(true);
    }

    @GetMapping("/tree")
    @Operation(summary = "查询商品分类树")
    @PreAuthorize("@ss.hasPermission('commerce:product-category:query')")
    public CommonResult<List<ProductCategoryTreeRespVO>> tree() {
        return success(categoryService.getCategoryTree());
    }
}
