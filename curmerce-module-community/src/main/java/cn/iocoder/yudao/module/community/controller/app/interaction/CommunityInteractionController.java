package cn.iocoder.yudao.module.community.controller.app.interaction;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.community.controller.app.interaction.vo.*;
import cn.iocoder.yudao.module.community.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 社区互动")
@RestController
@RequestMapping("/community")
@Validated
public class CommunityInteractionController {
    @Resource private CommunityService communityService;
    @PutMapping("/post/reaction") @Operation(summary = "点赞或收藏帖子")
    public CommonResult<Boolean> reaction(@Valid @RequestBody CommunityReactionReqVO req) { communityService.setReaction(getLoginUserId(), req); return success(true); }
    @PutMapping("/follow") @Operation(summary = "关注或取消关注用户")
    public CommonResult<Boolean> follow(@Valid @RequestBody CommunityFollowReqVO req) { communityService.setFollow(getLoginUserId(), req); return success(true); }
}
