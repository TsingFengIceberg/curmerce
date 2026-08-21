package cn.iocoder.yudao.module.commerce.controller.admin.release;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleaseCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleasePageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleaseRespVO;
import cn.iocoder.yudao.module.commerce.service.release.ReleaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Curmerce 限时发售")
@RestController
@RequestMapping("/commerce/release")
@Validated
public class ReleaseController {
    @Resource private ReleaseService releaseService;

    @PostMapping("/create") @Operation(summary = "创建限时发售活动")
    @PreAuthorize("@ss.hasPermission('commerce:release:create')")
    public CommonResult<Long> create(@Valid @RequestBody ReleaseCreateReqVO reqVO) { return success(releaseService.create(reqVO)); }
    @GetMapping("/page") @Operation(summary = "查询我的限时发售活动")
    @PreAuthorize("@ss.hasPermission('commerce:release:query')")
    public CommonResult<PageResult<ReleaseRespVO>> page(@Valid ReleasePageReqVO reqVO) { return success(releaseService.getOwnPage(reqVO)); }
    @PutMapping("/publish") @Operation(summary = "发布限时发售活动")
    @PreAuthorize("@ss.hasPermission('commerce:release:update')")
    public CommonResult<Boolean> publish(@RequestParam Long id) { releaseService.publish(id); return success(true); }
    @PutMapping("/cancel") @Operation(summary = "取消限时发售活动")
    @PreAuthorize("@ss.hasPermission('commerce:release:update')")
    public CommonResult<Boolean> cancel(@RequestParam Long id) { releaseService.cancel(id); return success(true); }
    @PutMapping("/finish") @Operation(summary = "结束限时发售活动")
    @PreAuthorize("@ss.hasPermission('commerce:release:update')")
    public CommonResult<Boolean> finish(@RequestParam Long id) { releaseService.finish(id); return success(true); }
}
