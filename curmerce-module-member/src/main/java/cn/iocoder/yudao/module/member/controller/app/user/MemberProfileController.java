package cn.iocoder.yudao.module.member.controller.app.user;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.user.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "Curmerce 买家资料")
@RestController
@RequestMapping("/member/profile")
@Validated
public class MemberProfileController {
    @Resource private MemberUserService userService;

    @GetMapping("/get") @Operation(summary = "查询买家资料")
    public CommonResult<MemberProfileRespVO> get() {
        return success(toResp(userService.requireActiveUser(getLoginUserId())));
    }
    @PutMapping("/update") @Operation(summary = "更新买家资料")
    public CommonResult<Boolean> update(@Valid @RequestBody MemberProfileUpdateReqVO reqVO) {
        userService.updateProfile(getLoginUserId(), reqVO);
        return success(true);
    }
    private MemberProfileRespVO toResp(MemberUserDO user) {
        return new MemberProfileRespVO().setId(user.getId()).setMobile(user.getMobile()).setNickname(user.getNickname())
                .setAvatar(user.getAvatar()).setEmail(user.getEmail()).setSex(user.getSex());
    }
}
