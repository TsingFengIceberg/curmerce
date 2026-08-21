package cn.iocoder.yudao.module.community.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.community.controller.admin.vo.*;
import cn.iocoder.yudao.module.community.controller.app.post.vo.CommunityPostRespVO;
import cn.iocoder.yudao.module.community.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - Curmerce 社区审核")
@RestController
@RequestMapping("/community")
@Validated
public class CommunityAdminController {
    @Resource private CommunityService communityService;
    @GetMapping("/post/page") @Operation(summary = "查询社区帖子") @PreAuthorize("@ss.hasPermission('community:post:query')")
    public CommonResult<PageResult<CommunityPostRespVO>> postPage(@Valid CommunityPostAdminPageReqVO req) { return success(communityService.getAdminPosts(req)); }
    @PutMapping("/post/status") @Operation(summary = "修改帖子审核状态") @PreAuthorize("@ss.hasPermission('community:post:audit')")
    public CommonResult<Boolean> postStatus(@Valid @RequestBody CommunityPostStatusReqVO req) { communityService.setPostStatus(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(), req); return success(true); }
    @GetMapping("/report/page") @Operation(summary = "查询社区举报") @PreAuthorize("@ss.hasPermission('community:report:query')")
    public CommonResult<PageResult<CommunityReportRespVO>> reportPage(@Valid CommunityReportPageReqVO req) { return success(communityService.getAdminReports(req)); }
    @PutMapping("/report/review") @Operation(summary = "审核社区举报") @PreAuthorize("@ss.hasPermission('community:report:audit')")
    public CommonResult<Boolean> reportReview(@Valid @RequestBody CommunityReportReviewReqVO req) { communityService.reviewReport(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(), req); return success(true); }
}
