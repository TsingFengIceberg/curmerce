package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.module.member.controller.app.auth.vo.*;

public interface MemberAuthService {
    MemberAuthTokenRespVO register(MemberAuthRegisterReqVO reqVO);
    MemberAuthTokenRespVO login(MemberAuthLoginReqVO reqVO);
    MemberAuthTokenRespVO refreshToken(String refreshToken);
    void logout(String token);
}
