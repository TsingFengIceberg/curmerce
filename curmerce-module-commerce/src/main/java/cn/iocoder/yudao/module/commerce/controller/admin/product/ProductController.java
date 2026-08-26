package cn.iocoder.yudao.module.commerce.controller.admin.product;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductCreateOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductIdReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductPageOwnReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductOperationLogRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductUpdateOwnReqVO;
import cn.iocoder.yudao.module.commerce.service.product.ProductAggregate;
import cn.iocoder.yudao.module.commerce.service.product.ProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Curmerce 商品")
@RestController
@RequestMapping("/commerce/product")
@Validated
public class ProductController {

    @Resource
    private ProductService productService;

    @PostMapping("/create-own")
    @Operation(summary = "创建自己的商品")
    @PreAuthorize("@ss.hasPermission('commerce:product:self-create')")
    public CommonResult<Long> createOwn(@Valid @RequestBody ProductCreateOwnReqVO reqVO) {
        return success(productService.createOwnProduct(reqVO));
    }

    @PutMapping("/update-own")
    @Operation(summary = "修改自己的商品")
    @PreAuthorize("@ss.hasPermission('commerce:product:self-update')")
    public CommonResult<Boolean> updateOwn(@Valid @RequestBody ProductUpdateOwnReqVO reqVO) {
        productService.updateOwnProduct(reqVO);
        return success(true);
    }

    @GetMapping("/get-own")
    @Operation(summary = "查询自己的商品")
    @PreAuthorize("@ss.hasPermission('commerce:product:self-query')")
    public CommonResult<ProductRespVO> getOwn(@RequestParam("id") Long id) {
        return success(ProductRespAssembler.toResponse(productService.getOwnProduct(id)));
    }

    @GetMapping("/page-own")
    @Operation(summary = "查询自己的商品分页")
    @PreAuthorize("@ss.hasPermission('commerce:product:self-query')")
    public CommonResult<PageResult<ProductRespVO>> pageOwn(@Valid ProductPageOwnReqVO reqVO) {
        return success(toResponsePage(productService.getOwnProductPage(reqVO)));
    }

    @GetMapping("/operation-log-own")
    @Operation(summary = "分页查询自己的商品操作记录")
    @PreAuthorize("@ss.hasPermission('commerce:product:self-query')")
    public CommonResult<PageResult<ProductOperationLogRespVO>> operationLogOwn(
            @RequestParam Long productId, @Valid PageParam pageParam) {
        return success(productService.getOwnOperationLogPage(productId, pageParam));
    }

    @PutMapping("/submit-own")
    @Operation(summary = "提交自己的商品审核")
    @PreAuthorize("@ss.hasPermission('commerce:product:self-submit')")
    public CommonResult<Boolean> submitOwn(@Valid @RequestBody ProductIdReqVO reqVO) {
        productService.submitOwnProduct(reqVO.getId());
        return success(true);
    }

    @PutMapping("/list-own")
    @Operation(summary = "上架自己的商品")
    @PreAuthorize("@ss.hasPermission('commerce:product:self-publish')")
    public CommonResult<Boolean> listOwn(@Valid @RequestBody ProductIdReqVO reqVO) {
        productService.listOwnProduct(reqVO.getId());
        return success(true);
    }

    @PutMapping("/delist-own")
    @Operation(summary = "下架自己的商品")
    @PreAuthorize("@ss.hasPermission('commerce:product:self-publish')")
    public CommonResult<Boolean> delistOwn(@Valid @RequestBody ProductIdReqVO reqVO) {
        productService.delistOwnProduct(reqVO.getId());
        return success(true);
    }

    private PageResult<ProductRespVO> toResponsePage(PageResult<ProductAggregate> page) {
        return new PageResult<>(page.getList().stream().map(ProductRespAssembler::toResponse).toList(), page.getTotal());
    }
}
