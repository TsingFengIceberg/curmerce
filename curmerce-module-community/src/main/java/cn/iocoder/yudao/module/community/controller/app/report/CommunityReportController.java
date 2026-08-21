package cn.iocoder.yudao.module.community.controller.app.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.community.controller.app.report.vo.CommunityReportCreateReqVO;
import cn.iocoder.yudao.module.community.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 社区举报")
@RestController
@RequestMapping("/community/report")
@Validated
public class CommunityReportController {
    @Resource private CommunityService communityService;
    @PostMapping("/create") @Operation(summary = "举报帖子")
    public CommonResult<Long> create(@Valid @RequestBody CommunityReportCreateReqVO req) { return success(communityService.report(getLoginUserId(), req)); }
}
