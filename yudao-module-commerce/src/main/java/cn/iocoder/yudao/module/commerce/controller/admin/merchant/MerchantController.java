package cn.iocoder.yudao.module.commerce.controller.admin.merchant;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.commerce.controller.admin.merchant.vo.*;
import cn.iocoder.yudao.module.commerce.dal.dataobject.merchant.MerchantDO;
import cn.iocoder.yudao.module.commerce.service.merchant.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Curmerce 商家")
@RestController
@RequestMapping("/commerce/merchant")
@Validated
public class MerchantController {
    @Resource private MerchantService merchantService;

    @PostMapping("/create")
    @Operation(summary = "创建待审核商家")
    @PreAuthorize("@ss.hasPermission('commerce:merchant:create')")
    public CommonResult<Long> create(@Valid @RequestBody MerchantCreateReqVO reqVO) {
        return success(merchantService.createMerchant(reqVO));
    }

    @GetMapping("/page")
    @Operation(summary = "查询商家分页")
    @PreAuthorize("@ss.hasPermission('commerce:merchant:query')")
    public CommonResult<PageResult<MerchantRespVO>> page(@Valid MerchantPageReqVO reqVO) {
        return success(BeanUtils.toBean(merchantService.getMerchantPage(reqVO), MerchantRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "查询商家")
    @PreAuthorize("@ss.hasPermission('commerce:merchant:query')")
    public CommonResult<MerchantRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(merchantService.getMerchant(id), MerchantRespVO.class));
    }

    @PutMapping("/approve")
    @Operation(summary = "审核通过商家")
    @ApiAccessLog(requestEnable = false)
    @PreAuthorize("@ss.hasPermission('commerce:merchant:audit')")
    public CommonResult<Boolean> approve(@Valid @RequestBody MerchantApproveReqVO reqVO) {
        merchantService.approveMerchant(reqVO);
        return success(true);
    }

    @PutMapping("/reject")
    @Operation(summary = "拒绝商家")
    @PreAuthorize("@ss.hasPermission('commerce:merchant:audit')")
    public CommonResult<Boolean> reject(@Valid @RequestBody MerchantRejectReqVO reqVO) {
        merchantService.rejectMerchant(reqVO);
        return success(true);
    }
}
