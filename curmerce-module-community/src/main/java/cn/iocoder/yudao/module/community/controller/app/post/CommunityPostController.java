package cn.iocoder.yudao.module.community.controller.app.post;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.community.controller.app.post.vo.*;
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

@Tag(name = "Curmerce 社区帖子")
@RestController
@RequestMapping("/community/post")
@Validated
public class CommunityPostController {
    @Resource private CommunityService communityService;

    @PostMapping("/create") @Operation(summary = "创建帖子草稿")
    public CommonResult<Long> create(@Valid @RequestBody CommunityPostCreateReqVO req) { return success(communityService.createPost(getLoginUserId(), req)); }
    @PutMapping("/update") @Operation(summary = "修改帖子草稿")
    public CommonResult<Boolean> update(@Valid @RequestBody CommunityPostUpdateReqVO req) { communityService.updatePost(getLoginUserId(), req); return success(true); }
    @PutMapping("/submit") @Operation(summary = "提交帖子")
    public CommonResult<Boolean> submit(@RequestParam Long id) { communityService.submitPost(getLoginUserId(), id); return success(true); }
    @DeleteMapping("/delete") @Operation(summary = "隐藏自己的帖子")
    public CommonResult<Boolean> delete(@RequestParam Long id) { communityService.deletePost(getLoginUserId(), id); return success(true); }
    @GetMapping("/page") @PermitAll @Operation(summary = "查询公开帖子流")
    public CommonResult<PageResult<CommunityPostRespVO>> page(@Valid CommunityPostPageReqVO req) { return success(communityService.getFeed(getLoginUserId(), req)); }
    @GetMapping("/get") @PermitAll @Operation(summary = "查询帖子详情")
    public CommonResult<CommunityPostRespVO> get(@RequestParam Long id) { return success(communityService.getPost(getLoginUserId(), id)); }
    @GetMapping("/my-page") @Operation(summary = "查询我的社区帖子")
    public CommonResult<PageResult<CommunityPostRespVO>> myPage(@Valid CommunityPostOwnerPageReqVO req) {
        return success(communityService.getOwnerPosts(getLoginUserId(), req));
    }
}
