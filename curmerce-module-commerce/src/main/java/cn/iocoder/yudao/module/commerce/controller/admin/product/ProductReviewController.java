package cn.iocoder.yudao.module.commerce.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductIdReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductRejectReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductReviewPageReqVO;
import cn.iocoder.yudao.module.commerce.service.product.ProductAggregate;
import cn.iocoder.yudao.module.commerce.service.product.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Curmerce 商品审核")
@RestController
@RequestMapping("/commerce/product-review")
@Validated
public class ProductReviewController {

    @Resource
    private ProductService productService;

    @GetMapping("/page")
    @Operation(summary = "查询商品审核分页")
    @PreAuthorize("@ss.hasPermission('commerce:product:query')")
    public CommonResult<PageResult<ProductRespVO>> page(@Valid ProductReviewPageReqVO reqVO) {
        return success(toResponsePage(productService.getProductReviewPage(reqVO)));
    }

    @GetMapping("/get")
    @Operation(summary = "查询商品审核详情")
    @PreAuthorize("@ss.hasPermission('commerce:product:query')")
    public CommonResult<ProductRespVO> get(@RequestParam("id") Long id) {
        return success(ProductRespAssembler.toResponse(productService.getProductForReview(id)));
    }

    @PutMapping("/approve")
    @Operation(summary = "审核通过商品")
    @PreAuthorize("@ss.hasPermission('commerce:product:audit')")
    public CommonResult<Boolean> approve(@Valid @RequestBody ProductIdReqVO reqVO) {
        productService.approveProduct(reqVO.getId());
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "驳回商品")
    @PreAuthorize("@ss.hasPermission('commerce:product:audit')")
    public CommonResult<Boolean> reject(@Valid @RequestBody ProductRejectReqVO reqVO) {
        productService.rejectProduct(reqVO);
        return success(true);
    }

    private PageResult<ProductRespVO> toResponsePage(PageResult<ProductAggregate> page) {
        return new PageResult<>(page.getList().stream().map(ProductRespAssembler::toResponse).toList(), page.getTotal());
    }
}
