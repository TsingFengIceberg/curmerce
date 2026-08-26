package cn.iocoder.yudao.module.commerce.controller.app.personal;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.product.ProductOperationLogRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.personal.vo.*;
import cn.iocoder.yudao.module.commerce.service.personal.PersonalListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 个人卖家商品")
@RestController
@RequestMapping("/commerce/personal-listing")
@Validated
public class PersonalListingController {
    @Resource private PersonalListingService listingService;

    @PostMapping("/create") @Operation(summary = "创建个人商品草稿")
    public CommonResult<Long> create(@Valid @RequestBody PersonalListingCreateReqVO req) { return success(listingService.create(getLoginUserId(), req)); }
    @PutMapping("/update") @Operation(summary = "修改个人商品草稿")
    public CommonResult<Boolean> update(@Valid @RequestBody PersonalListingUpdateReqVO req) { listingService.update(getLoginUserId(), req); return success(true); }
    @GetMapping("/get") @Operation(summary = "查询自己的个人商品")
    public CommonResult<PersonalListingRespVO> get(@RequestParam Long id) { return success(listingService.get(getLoginUserId(), id)); }
    @GetMapping("/page") @Operation(summary = "查询自己的个人商品列表")
    public CommonResult<PageResult<PersonalListingRespVO>> page(@Valid PersonalListingPageReqVO req) { return success(listingService.page(getLoginUserId(), req)); }
    @GetMapping("/operation-log") @Operation(summary = "分页查询个人商品操作记录")
    public CommonResult<PageResult<ProductOperationLogRespVO>> operationLog(@RequestParam Long productId, @Valid PageParam pageParam) {
        return success(listingService.getOperationLogPage(getLoginUserId(), productId, pageParam));
    }
    @PutMapping("/submit") @Operation(summary = "提交个人商品审核")
    public CommonResult<Boolean> submit(@RequestParam Long id) { listingService.submit(getLoginUserId(), id); return success(true); }
    @PutMapping("/list") @Operation(summary = "上架个人商品")
    public CommonResult<Boolean> list(@RequestParam Long id) { listingService.list(getLoginUserId(), id); return success(true); }
    @PutMapping("/delist") @Operation(summary = "下架个人商品")
    public CommonResult<Boolean> delist(@RequestParam Long id) { listingService.delist(getLoginUserId(), id); return success(true); }
}
