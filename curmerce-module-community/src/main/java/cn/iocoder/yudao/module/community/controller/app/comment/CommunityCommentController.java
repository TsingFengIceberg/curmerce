package cn.iocoder.yudao.module.community.controller.app.comment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.community.controller.app.comment.vo.*;
import cn.iocoder.yudao.module.community.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 社区评论")
@RestController
@RequestMapping("/community/comment")
@Validated
public class CommunityCommentController {
    @Resource private CommunityService communityService;
    @PostMapping("/create") @Operation(summary = "发表评论或回复")
    public CommonResult<Long> create(@Valid @RequestBody CommunityCommentCreateReqVO req) { return success(communityService.createComment(getLoginUserId(), req)); }
    @GetMapping("/page") @PermitAll @Operation(summary = "查询帖子评论")
    public CommonResult<PageResult<CommunityCommentRespVO>> page(@Valid CommunityCommentPageReqVO req) { return success(communityService.getComments(getLoginUserId(), req)); }
}
