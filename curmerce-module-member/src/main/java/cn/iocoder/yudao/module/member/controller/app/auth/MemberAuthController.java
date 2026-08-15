package cn.iocoder.yudao.module.member.controller.app.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.*;
import cn.iocoder.yudao.module.member.service.auth.MemberAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "Curmerce 买家认证")
@RestController
@RequestMapping("/member/auth")
@Validated
public class MemberAuthController {
    @Resource private MemberAuthService authService;
    @Resource private SecurityProperties securityProperties;

    @PostMapping("/register") @PermitAll @Operation(summary = "买家注册")
    public CommonResult<MemberAuthTokenRespVO> register(@Valid @RequestBody MemberAuthRegisterReqVO reqVO) {
        return success(authService.register(reqVO));
    }
    @PostMapping("/login") @PermitAll @Operation(summary = "买家登录")
    public CommonResult<MemberAuthTokenRespVO> login(@Valid @RequestBody MemberAuthLoginReqVO reqVO) {
        return success(authService.login(reqVO));
    }
    @PostMapping("/refresh-token") @PermitAll
    public CommonResult<MemberAuthTokenRespVO> refresh(@RequestParam String refreshToken) {
        return success(authService.refreshToken(refreshToken));
    }
    @PostMapping("/logout") @PermitAll
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request, securityProperties.getTokenHeader(),
                securityProperties.getTokenParameter());
        if (StrUtil.isNotBlank(token)) authService.logout(token);
        return success(true);
    }
}
