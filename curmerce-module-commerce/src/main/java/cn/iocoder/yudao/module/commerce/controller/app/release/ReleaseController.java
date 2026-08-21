package cn.iocoder.yudao.module.commerce.controller.app.release;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.*;
import cn.iocoder.yudao.module.commerce.service.release.ReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 限时发售")
@RestController("commerceAppReleaseController")
@RequestMapping("/commerce/release")
@Validated
public class ReleaseController {
    @Resource private ReleaseService releaseService;
    @GetMapping("/page") @PermitAll @Operation(summary = "查询公开限时发售活动")
    public CommonResult<PageResult<ReleaseRespVO>> page(@Valid cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePageReqVO reqVO) {
        return success(releaseService.getPublicPage(reqVO));
    }
    @GetMapping("/get") @PermitAll @Operation(summary = "查询限时发售详情")
    public CommonResult<ReleaseRespVO> get(@RequestParam Long id) { return success(releaseService.get(id, true)); }
    @PostMapping("/purchase") @Operation(summary = "购买限时发售商品")
    public CommonResult<ReleasePurchaseRespVO> purchase(@Valid @RequestBody ReleasePurchaseReqVO reqVO) {
        return success(releaseService.purchase(getLoginUserId(), reqVO));
    }
}
