package cn.iocoder.yudao.module.commerce.controller.app.catalog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.catalog.vo.*;
import cn.iocoder.yudao.module.commerce.service.catalog.PublicCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "Curmerce 公共商品目录")
@RestController
@RequestMapping("/commerce/catalog")
@Validated
public class PublicCatalogController {
    @Resource private PublicCatalogService catalogService;
    @GetMapping("/category-tree") @PermitAll @Operation(summary = "查询公开分类树")
    public CommonResult<List<PublicCategoryNodeRespVO>> categoryTree() { return success(catalogService.getCategoryTree()); }
    @GetMapping("/product-page") @PermitAll @Operation(summary = "分页查询公开商品")
    public CommonResult<PageResult<PublicProductSummaryRespVO>> productPage(@Valid PublicProductPageReqVO reqVO) { return success(catalogService.getProductPage(reqVO)); }
    @GetMapping("/product-detail") @PermitAll @Operation(summary = "查询公开商品详情")
    public CommonResult<PublicProductDetailRespVO> productDetail(@RequestParam Long id) { return success(catalogService.getProductDetail(id)); }
}
