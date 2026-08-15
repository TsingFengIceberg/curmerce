package cn.iocoder.yudao.module.member.service.auth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCreateReqDTO;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

@Service
public class MemberAuthServiceImpl implements MemberAuthService {
    @Resource private MemberUserService userService;
    @Resource private OAuth2TokenCommonApi oauth2TokenApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberAuthTokenRespVO register(MemberAuthRegisterReqVO reqVO) {
        MemberUserDO user = userService.register(reqVO, ServletUtils.getClientIP());
        return createToken(user.getId());
    }

    @Override
    public MemberAuthTokenRespVO login(MemberAuthLoginReqVO reqVO) {
        String mobile = StrUtil.trim(reqVO.getMobile());
        MemberUserDO user = userService.getUserByMobile(mobile);
        if (user == null || !userService.isPasswordMatch(reqVO.getPassword(), user.getPassword())) {
            throw exception(BAD_CREDENTIALS);
        }
        if (CommonStatusEnum.isDisable(user.getStatus())) throw exception(USER_DISABLED);
        userService.updateLogin(user.getId(), ServletUtils.getClientIP());
        return createToken(user.getId());
    }

    @Override
    public MemberAuthTokenRespVO refreshToken(String refreshToken) {
        if (StrUtil.isBlank(refreshToken)) throw exception(BAD_CREDENTIALS);
        return convert(oauth2TokenApi.refreshAccessToken(refreshToken, OAuth2ClientConstants.CLIENT_ID_DEFAULT));
    }

    @Override
    public void logout(String token) {
        if (StrUtil.isNotBlank(token)) oauth2TokenApi.removeAccessToken(token);
    }

    private MemberAuthTokenRespVO createToken(Long userId) {
        return convert(oauth2TokenApi.createAccessToken(new OAuth2AccessTokenCreateReqDTO()
                .setUserId(userId).setUserType(UserTypeEnum.MEMBER.getValue())
                .setClientId(OAuth2ClientConstants.CLIENT_ID_DEFAULT)));
    }

    private MemberAuthTokenRespVO convert(OAuth2AccessTokenRespDTO token) {
        if (token == null) throw exception(BAD_CREDENTIALS);
        return new MemberAuthTokenRespVO().setUserId(token.getUserId()).setAccessToken(token.getAccessToken())
                .setRefreshToken(token.getRefreshToken()).setExpiresTime(token.getExpiresTime());
    }
}
